package id.herbatech.erp.shared.document;

import id.herbatech.erp.shared.activity.ActivityService;
import id.herbatech.erp.shared.approval.ApprovalService;
import id.herbatech.erp.shared.attachment.AttachmentService;
import id.herbatech.erp.shared.domain.DocumentEntity;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.esign.ESignatureService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * API generik untuk semua jenis dokumen: aksi status (pipeline), dan isi panel kanan form
 * (riwayat aktivitas, approval, tanda tangan, lampiran).
 */
@RestController
@RequestMapping("/api/documents/{docType}/{id}")
public class DocumentController {

    private final DocumentWorkflowService workflow;
    private final ApprovalService approvals;
    private final ActivityService activity;
    private final AttachmentService attachments;
    private final ESignatureService esign;
    private final DocumentAccess access;
    private final List<RelatedDocumentProvider> relatedProviders;

    public DocumentController(DocumentWorkflowService workflow, ApprovalService approvals, ActivityService activity,
                              AttachmentService attachments, ESignatureService esign, DocumentAccess access,
                              List<RelatedDocumentProvider> relatedProviders) {
        this.workflow = workflow;
        this.approvals = approvals;
        this.activity = activity;
        this.attachments = attachments;
        this.esign = esign;
        this.access = access;
        this.relatedProviders = relatedProviders;
    }

    public record ActionRequest(String reason, String password, LocalDate date) {
    }

    public record ActionResult(Long id, String docNo, String status) {
    }

    public record Comment(String message) {
    }

    public record Panel(List<ActivityService.ActivityView> activity, List<ApprovalService.TaskView> approvals,
                        List<ESignatureService.SignatureView> signatures,
                        List<AttachmentService.AttachmentView> attachments,
                        List<RelatedDocumentProvider.RelatedDoc> related, Long decidableTaskId, boolean decisionRequiresEsign) {
    }

    @PostMapping("/submit")
    public ActionResult submit(@PathVariable String docType, @PathVariable Long id) {
        workflow.submit(docType, id);
        return result(docType, id);
    }

    @PostMapping("/post")
    public ActionResult post(@PathVariable String docType, @PathVariable Long id, @RequestBody(required = false) ActionRequest req) {
        workflow.post(docType, id, req == null ? null : req.password());
        return result(docType, id);
    }

    @PostMapping("/cancel")
    public ActionResult cancel(@PathVariable String docType, @PathVariable Long id, @RequestBody ActionRequest req) {
        workflow.cancel(docType, id, req.reason());
        return result(docType, id);
    }

    @PostMapping("/reverse")
    public ActionResult reverse(@PathVariable String docType, @PathVariable Long id, @RequestBody ActionRequest req) {
        DocumentEntity reversal = workflow.reverse(docType, id, req.reason(), req.date());
        return new ActionResult(reversal.getId(), reversal.getDocNo(), reversal.getStatus().name());
    }

    /** Isi panel kanan form dokumen dalam satu panggilan. */
    @GetMapping("/panel")
    public Panel panel(@PathVariable String docType, @PathVariable Long id) {
        requireView(docType, id);
        List<RelatedDocumentProvider.RelatedDoc> related = relatedProviders.stream()
                .flatMap(p -> p.related(docType, id).stream()).toList();
        ApprovalService.TaskView mine = approvals.inbox(null).stream()
                .filter(t -> t.docType().equals(docType) && t.docId().equals(id)).findFirst().orElse(null);
        return new Panel(activity.list(docType, id), approvals.forDocument(docType, id), esign.list(docType, id),
                attachments.list(docType, id), related, mine == null ? null : mine.id(), mine != null && mine.requiresEsign());
    }

    @PostMapping("/comments")
    public ResponseEntity<Void> comment(@PathVariable String docType, @PathVariable Long id, @RequestBody Comment c) {
        requireView(docType, id);
        if (c.message() == null || c.message().isBlank()) {
            throw new BusinessException("EMPTY", "Komentar kosong");
        }
        activity.log(docType, id, "COMMENT", c.message().trim());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/attachments")
    public AttachmentService.AttachmentView upload(@PathVariable String docType, @PathVariable Long id,
                                                   @RequestPart("file") MultipartFile file) {
        requireView(docType, id);
        return attachments.store(docType, id, file);
    }

    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable String docType, @PathVariable Long id,
                                             @PathVariable Long attachmentId,
                                             @RequestParam(defaultValue = "false") boolean inline) {
        requireView(docType, id);
        AttachmentService.StoredFile f = attachments.load(docType, id, attachmentId);
        ContentDisposition cd = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(f.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(f.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(f.contentType()))
                .body(new FileSystemResource(f.path()));
    }

    private void requireView(String docType, Long id) {
        DocumentHandler<DocumentEntity> h = workflow.handler(docType);
        DocumentEntity doc = h.load(id);
        access.requireView(workflow.type(docType), doc, h.requesterId(doc));
    }

    private ActionResult result(String docType, Long id) {
        DocumentEntity doc = workflow.handler(docType).load(id);
        return new ActionResult(doc.getId(), doc.getDocNo(), doc.getStatus().name());
    }
}
