package id.herbatech.erp.shared.document;

import id.herbatech.erp.shared.activity.ActivityService;
import id.herbatech.erp.shared.approval.ApprovalEvents;
import id.herbatech.erp.shared.approval.ApprovalService;
import id.herbatech.erp.shared.audit.AuditContext;
import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.domain.DocStatus;
import id.herbatech.erp.shared.domain.DocumentEntity;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.esign.ESignatureService;
import id.herbatech.erp.shared.period.PeriodLockService;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.CurrentUser;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Satu-satunya pengubah status dokumen (PRD §13 "Siklus status dokumen"):
 * Draft → Diajukan → Disetujui → Diposting, cabang Ditolak & Dibatalkan, koreksi via reversal.
 * Setiap transisi: cek hak akses, cek kunci periode, catat aktivitas + audit, perbarui indeks, terbitkan event.
 */
@Service
public class DocumentWorkflowService {

    private final Map<String, DocumentHandler<? extends DocumentEntity>> handlers = new HashMap<>();
    private final DocTypeRepository docTypes;
    private final NumberingService numbering;
    private final DocumentIndexService index;
    private final ApprovalService approvals;
    private final PermissionService perm;
    private final PeriodLockService periods;
    private final ESignatureService esign;
    private final ActivityService activity;
    private final ApplicationEventPublisher events;
    private final TimeService time;

    public DocumentWorkflowService(List<DocumentHandler<? extends DocumentEntity>> handlerList, DocTypeRepository docTypes,
                                   NumberingService numbering, DocumentIndexService index, ApprovalService approvals,
                                   PermissionService perm, PeriodLockService periods, ESignatureService esign,
                                   ActivityService activity, ApplicationEventPublisher events, TimeService time) {
        handlerList.forEach(h -> handlers.put(h.docType(), h));
        this.docTypes = docTypes;
        this.numbering = numbering;
        this.index = index;
        this.approvals = approvals;
        this.perm = perm;
        this.periods = periods;
        this.esign = esign;
        this.activity = activity;
        this.events = events;
        this.time = time;
    }

    // ------------------------------------------------------------------ pembuatan & penyimpanan draft

    /** Menyiapkan dokumen baru: plant aktif, nomor dokumen, status Draft. Panggil sebelum save pertama. */
    @Transactional
    public <D extends DocumentEntity> D initDraft(DocumentHandler<D> handler, D doc) {
        DocType type = type(handler.docType());
        requireOrSelf(type, Action.CREATE, null);
        CurrentUser me = UserContext.current();
        if (doc.getDocDate() == null) {
            doc.setDocDate(time.today());
        }
        periods.assertDateAllowed(handler.periodModule(), doc.getDocDate());
        doc.setPlantId(me.plantId());
        doc.setStatus(DocStatus.DRAFT);
        doc.setDocNo(numbering.next(type.getCode(), me.plantId(), doc.getDocDate()));
        D saved = handler.save(doc);
        activity.status(type.getCode(), saved.getId(), null, DocStatus.DRAFT.name(), "Dokumen dibuat");
        index.upsert(type, saved, handler.summary(saved), handler.amount(saved));
        return saved;
    }

    /**
     * Draft yang dibuat sistem sebagai akibat dokumen lain (mis. uang muka kerja dari SPD yang disetujui).
     * Tidak memeriksa hak pengguna yang sedang login; tetap memakai nomor, indeks, dan riwayat standar.
     */
    @Transactional
    public <D extends DocumentEntity> D initSystemDraft(DocumentHandler<D> handler, D doc, Long plantId, String note) {
        DocType type = type(handler.docType());
        if (doc.getDocDate() == null) {
            doc.setDocDate(time.today());
        }
        doc.setPlantId(plantId);
        doc.setStatus(DocStatus.DRAFT);
        doc.setDocNo(numbering.next(type.getCode(), plantId, doc.getDocDate()));
        D saved = handler.save(doc);
        activity.status(type.getCode(), saved.getId(), null, DocStatus.DRAFT.name(), note);
        index.upsert(type, saved, handler.summary(saved), handler.amount(saved));
        return saved;
    }

    /** Pastikan dokumen masih boleh diedit oleh pengguna ini (Draft/Ditolak, pembuat atau Manager). */
    public <D extends DocumentEntity> void assertEditable(DocumentHandler<D> handler, D doc) {
        DocType type = type(handler.docType());
        if (!doc.getStatus().isEditable()) {
            throw new BusinessException("NOT_EDITABLE", "Dokumen berstatus " + doc.getStatus().label()
                    + " tidak bisa diubah. Koreksi dilakukan lewat pembatalan atau reversal.");
        }
        CurrentUser me = UserContext.current();
        if (!me.id().equals(doc.getCreatedBy()) && !perm.has(type.getMenuCode(), Action.POST)) {
            throw new BusinessException("NOT_OWNER", "Hanya pembuat dokumen atau Manager yang boleh mengubah draft ini");
        }
        requireOrSelf(type, Action.EDIT, doc);
        periods.assertDateAllowed(handler.periodModule(), doc.getDocDate());
    }

    /** Panggil setelah draft diubah agar indeks dokumen ikut diperbarui. */
    @Transactional
    public <D extends DocumentEntity> void touched(DocumentHandler<D> handler, D doc) {
        index.upsert(type(handler.docType()), doc, handler.summary(doc), handler.amount(doc));
    }

    // ------------------------------------------------------------------ transisi status

    @Transactional
    public void submit(String docType, Long id) {
        DocumentHandler<DocumentEntity> h = handler(docType);
        DocType type = type(docType);
        DocumentEntity doc = h.load(id);
        requireOrSelf(type, Action.SUBMIT, doc);
        CurrentUser me = UserContext.current();
        if (!me.id().equals(doc.getCreatedBy()) && !perm.has(type.getMenuCode(), Action.POST)) {
            throw new BusinessException("NOT_OWNER", "Hanya pembuat dokumen yang boleh mengajukan");
        }
        requireStatus(doc, DocStatus.DRAFT, DocStatus.REJECTED);
        periods.assertDateAllowed(h.periodModule(), doc.getDocDate());
        h.validateSubmit(doc);

        DocStatus from = doc.getStatus();
        doc.setStatus(DocStatus.SUBMITTED);
        doc.setSubmittedAt(time.now());
        h.save(doc);
        changed(type, h, doc, from, null, "Diajukan");

        boolean needsApproval = approvals.start(type, doc, h.amount(doc), h.summary(doc), h.requesterId(doc),
                h.approvalFlags(doc));
        if (!needsApproval) {
            markApproved(type, h, doc, null);
        }
    }

    @EventListener
    public void onApprovalCompleted(ApprovalEvents.ApprovalCompleted e) {
        DocumentHandler<DocumentEntity> h = handler(e.docType());
        DocumentEntity doc = h.load(e.docId());
        markApproved(type(e.docType()), h, doc, e.lastApproverId());
    }

    @EventListener
    public void onApprovalRejected(ApprovalEvents.ApprovalRejected e) {
        DocumentHandler<DocumentEntity> h = handler(e.docType());
        DocType type = type(e.docType());
        DocumentEntity doc = h.load(e.docId());
        DocStatus from = doc.getStatus();
        doc.setStatus(DocStatus.REJECTED);
        h.save(doc);
        changed(type, h, doc, from, e.reason(), "Ditolak: " + e.reason());
    }

    private void markApproved(DocType type, DocumentHandler<DocumentEntity> h, DocumentEntity doc, Long approverId) {
        DocStatus from = doc.getStatus();
        doc.setStatus(DocStatus.APPROVED);
        doc.setApprovedBy(approverId);
        doc.setApprovedAt(time.now());
        h.save(doc);
        changed(type, h, doc, from, null, approverId == null ? "Disetujui otomatis (tidak ada level approval)" : "Disetujui");
        h.onApproved(doc);
        if (h.autoPost(doc)) {
            periods.assertDateAllowed(h.periodModule(), doc.getDocDate());
            h.onPost(doc);
            doc.setStatus(DocStatus.POSTED);
            doc.setPostedBy(UserContext.currentOptional().map(CurrentUser::id).orElse(approverId));
            doc.setPostedAt(time.now());
            h.save(doc);
            changed(type, h, doc, DocStatus.APPROVED, null, "Diposting otomatis setelah disetujui");
        }
    }

    @Transactional
    public void post(String docType, Long id, String password) {
        DocumentHandler<DocumentEntity> h = handler(docType);
        DocType type = type(docType);
        DocumentEntity doc = h.load(id);
        perm.require(type.getMenuCode(), Action.POST);
        requireStatus(doc, DocStatus.APPROVED);
        periods.assertDateAllowed(h.periodModule(), doc.getDocDate());
        if (type.isRequiresEsign()) {
            esign.sign(docType, id, ESignatureService.Meaning.APPROVE, password, "Posting");
        }
        h.onPost(doc);
        DocStatus from = doc.getStatus();
        doc.setStatus(DocStatus.POSTED);
        doc.setPostedBy(UserContext.userId());
        doc.setPostedAt(time.now());
        h.save(doc);
        changed(type, h, doc, from, null, "Diposting");
    }

    @Transactional
    public void cancel(String docType, Long id, String reason) {
        requireReason(reason);
        DocumentHandler<DocumentEntity> h = handler(docType);
        DocType type = type(docType);
        DocumentEntity doc = h.load(id);
        CurrentUser me = UserContext.current();
        DocStatus from = doc.getStatus();
        switch (from) {
            case DRAFT, REJECTED, SUBMITTED -> {
                if (!me.id().equals(doc.getCreatedBy())) {
                    perm.require(type.getMenuCode(), Action.CANCEL);
                }
            }
            case APPROVED -> perm.require(type.getMenuCode(), Action.CANCEL);
            case POSTED, DONE -> throw new BusinessException("USE_REVERSAL",
                    "Dokumen yang sudah diposting tidak bisa dibatalkan langsung. Gunakan reversal.");
            case CANCELLED -> throw new BusinessException("ALREADY_CANCELLED", "Dokumen sudah dibatalkan");
        }
        approvals.cancelOpen(docType, id);
        h.onCancel(doc);
        AuditContext.withReason(reason, () -> {
            doc.setStatus(DocStatus.CANCELLED);
            doc.setCancelledBy(me.id());
            doc.setCancelledAt(time.now());
            doc.setCancelReason(reason);
            return h.save(doc);
        });
        changed(type, h, doc, from, reason, "Dibatalkan: " + reason);
    }

    /** Reversal dokumen terposting: membuat dokumen balik yang merujuk dokumen asal; dokumen asal menjadi Dibatalkan. */
    @Transactional
    public DocumentEntity reverse(String docType, Long id, String reason, LocalDate date) {
        requireReason(reason);
        DocumentHandler<DocumentEntity> h = handler(docType);
        DocType type = type(docType);
        DocumentEntity doc = h.load(id);
        perm.require(type.getMenuCode(), Action.CANCEL);
        requireStatus(doc, DocStatus.POSTED);
        LocalDate reversalDate = date == null ? time.today() : date;
        periods.assertDateAllowed(h.periodModule(), reversalDate);

        DocumentEntity reversal = AuditContext.withReason(reason, () -> h.reverse(doc, reversalDate, reason));
        index.upsert(type, reversal, h.summary(reversal), h.amount(reversal));
        activity.status(docType, reversal.getId(), null, reversal.getStatus().name(), "Reversal atas " + doc.getDocNo() + ": " + reason);

        DocStatus from = doc.getStatus();
        CurrentUser me = UserContext.current();
        AuditContext.withReason(reason, () -> {
            doc.setStatus(DocStatus.CANCELLED);
            doc.setCancelledBy(me.id());
            doc.setCancelledAt(time.now());
            doc.setCancelReason("Reversal " + reversal.getDocNo() + ": " + reason);
            return h.save(doc);
        });
        changed(type, h, doc, from, reason, "Dibatalkan dengan reversal " + reversal.getDocNo() + ": " + reason);
        return reversal;
    }

    /**
     * Tombol aksi yang boleh tampil untuk pengguna saat ini (PRD §15.2: "tombol aksi sesuai status").
     * Aturan sama dengan pengecekan saat aksi dijalankan.
     */
    public List<String> allowedActions(String docType, DocumentEntity doc) {
        DocumentHandler<DocumentEntity> h = handler(docType);
        DocType type = type(docType);
        String menu = type.getMenuCode();
        CurrentUser me = UserContext.current();
        boolean owner = me.id().equals(doc.getCreatedBy());
        boolean manager = perm.has(menu, Action.POST);
        List<String> out = new java.util.ArrayList<>();
        DocStatus s = doc.getStatus();
        boolean self = owner && type.getEssMenuCode() != null;
        if (s.isEditable() && (owner || manager) && (self || perm.has(menu, Action.EDIT))) {
            out.add("EDIT");
        }
        if (s.isEditable() && (owner || manager) && (self || perm.has(menu, Action.SUBMIT))) {
            out.add("SUBMIT");
        }
        if (s == DocStatus.APPROVED && manager) {
            out.add("POST");
        }
        if (((s == DocStatus.DRAFT || s == DocStatus.REJECTED || s == DocStatus.SUBMITTED) && (owner || perm.has(menu, Action.CANCEL)))
                || (s == DocStatus.APPROVED && perm.has(menu, Action.CANCEL))) {
            out.add("CANCEL");
        }
        if (s == DocStatus.POSTED && perm.has(menu, Action.CANCEL)) {
            out.add("REVERSE");
        }
        out.removeIf(a -> !h.allows(a, doc));
        return out;
    }

    /** Menutup dokumen yang sudah selesai diproses (mis. WO setelah hasil produksi diposting). */
    @Transactional
    public void markDone(String docType, Long id, String message) {
        DocumentHandler<DocumentEntity> h = handler(docType);
        DocType type = type(docType);
        DocumentEntity doc = h.load(id);
        requireStatus(doc, DocStatus.APPROVED, DocStatus.POSTED);
        DocStatus from = doc.getStatus();
        doc.setStatus(DocStatus.DONE);
        h.save(doc);
        changed(type, h, doc, from, null, message);
    }

    /** Hak aksi; dokumen layanan mandiri boleh dikelola pembuatnya sendiri. */
    private void requireOrSelf(DocType type, Action action, DocumentEntity doc) {
        boolean self = type.getEssMenuCode() != null
                && (doc == null || UserContext.current().id().equals(doc.getCreatedBy()));
        if (!self) {
            perm.require(type.getMenuCode(), action);
        }
    }

    // ------------------------------------------------------------------ util

    public DocType type(String code) {
        return docTypes.findByCode(code).orElseThrow(() -> new BusinessException("DOC_TYPE", "Jenis dokumen tidak dikenal: " + code));
    }

    @SuppressWarnings("unchecked")
    public DocumentHandler<DocumentEntity> handler(String docType) {
        DocumentHandler<? extends DocumentEntity> h = handlers.get(docType);
        if (h == null) {
            throw new BusinessException("DOC_TYPE", "Dokumen " + docType + " belum tersedia di versi ini");
        }
        return (DocumentHandler<DocumentEntity>) h;
    }

    public boolean supports(String docType) {
        return handlers.containsKey(docType);
    }

    private void changed(DocType type, DocumentHandler<DocumentEntity> h, DocumentEntity doc, DocStatus from,
                         String reason, String message) {
        activity.status(type.getCode(), doc.getId(), from.name(), doc.getStatus().name(), message);
        index.upsert(type, doc, h.summary(doc), h.amount(doc));
        events.publishEvent(new DocumentStatusChanged(type.getCode(), doc.getId(), doc.getDocNo(), type.getAppCode(),
                doc.getPlantId(), from.name(), doc.getStatus().name(),
                UserContext.currentOptional().map(CurrentUser::id).orElse(null), doc.getCreatedBy(), reason));
    }

    private static void requireStatus(DocumentEntity doc, DocStatus... allowed) {
        for (DocStatus s : allowed) {
            if (doc.getStatus() == s) {
                return;
            }
        }
        throw new BusinessException("INVALID_STATUS", "Aksi tidak bisa dilakukan pada dokumen berstatus " + doc.getStatus().label());
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("REASON_REQUIRED", "Alasan wajib diisi untuk pembatalan atau reversal");
        }
    }
}
