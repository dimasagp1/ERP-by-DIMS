package id.herbatech.erp.shared.approval;

import id.herbatech.erp.shared.activity.ActivityService;
import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.document.DocType;
import id.herbatech.erp.shared.document.DocTypeRepository;
import id.herbatech.erp.shared.domain.DocumentEntity;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.esign.ESignatureService;
import id.herbatech.erp.shared.security.CurrentUser;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import id.herbatech.erp.shared.security.UserDirectory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mesin approval berjenjang (SYS-04, PRD §13).
 * <ul>
 *   <li>Level diambil dari matriks approval sesuai jenis dokumen, nilai, dan plant.</li>
 *   <li>Level berjalan berurutan: hanya satu level berstatus PENDING pada satu waktu.</li>
 *   <li>Pemisahan tugas: pembuat dokumen tidak bisa menyetujui, dan satu orang tidak bisa menyetujui dua level.</li>
 * </ul>
 */
@Service
public class ApprovalService {

    public record TaskView(Long id, String docType, Long docId, String docNo, String docSummary, BigDecimal docAmount,
                           String appCode, String menuCode, Long plantId, int level, String status, String assigneeLabel,
                           Long assigneeUserId, String assigneeName, Long requestedBy, String requestedByName,
                           Instant activatedAt, Long decidedBy, String decidedByName, Instant decidedAt,
                           String decisionReason, boolean requiresEsign) {
    }

    private final ApprovalRuleRepository rules;
    private final ApprovalTaskRepository tasks;
    private final DocTypeRepository docTypes;
    private final OrgDirectory org;
    private final UserDirectory users;
    private final PermissionService perm;
    private final ESignatureService esign;
    private final ActivityService activity;
    private final ApplicationEventPublisher events;
    private final TimeService time;
    private final JdbcTemplate jdbc;

    public ApprovalService(ApprovalRuleRepository rules, ApprovalTaskRepository tasks, DocTypeRepository docTypes,
                           OrgDirectory org, UserDirectory users, PermissionService perm, ESignatureService esign,
                           ActivityService activity, ApplicationEventPublisher events, TimeService time, JdbcTemplate jdbc) {
        this.rules = rules;
        this.tasks = tasks;
        this.docTypes = docTypes;
        this.org = org;
        this.users = users;
        this.perm = perm;
        this.esign = esign;
        this.activity = activity;
        this.events = events;
        this.time = time;
        this.jdbc = jdbc;
    }

    /**
     * Membuat tugas approval untuk dokumen yang baru diajukan.
     *
     * @return false bila tidak ada level yang berlaku (dokumen langsung dianggap disetujui)
     */
    @Transactional
    public boolean start(DocType type, DocumentEntity doc, BigDecimal amount, String summary, Long requesterId,
                         java.util.Set<String> flags) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        List<ApprovalRule> applicable = rules.findByDocTypeCodeAndActiveTrueOrderByLevelAsc(type.getCode()).stream()
                .filter(r -> r.getPlantId() == null || r.getPlantId().equals(doc.getPlantId()))
                .filter(r -> value.compareTo(r.getMinAmount()) >= 0)
                .filter(r -> r.getConditionKey() == null || r.getConditionKey().isBlank() || flags.contains(r.getConditionKey()))
                .toList();
        if (applicable.isEmpty()) {
            return false;
        }
        Instant now = time.now();
        List<ApprovalTask> created = new ArrayList<>();
        for (ApprovalRule rule : applicable) {
            ApprovalTask t = new ApprovalTask();
            t.setDocType(type.getCode());
            t.setDocId(doc.getId());
            t.setDocNo(doc.getDocNo());
            t.setDocSummary(summary);
            t.setDocAmount(amount);
            t.setAppCode(type.getAppCode());
            t.setPlantId(doc.getPlantId());
            t.setLevel(rule.getLevel());
            t.setRequestedBy(requesterId);
            assign(t, rule, type, requesterId);
            t.setStatus(created.isEmpty() ? ApprovalTask.Status.PENDING : ApprovalTask.Status.WAITING);
            if (created.isEmpty()) {
                t.setActivatedAt(now);
            }
            created.add(tasks.save(t));
        }
        events.publishEvent(new ApprovalEvents.ApprovalTaskActivated(created.getFirst().getId()));
        return true;
    }

    private void assign(ApprovalTask t, ApprovalRule rule, DocType type, Long requesterId) {
        switch (rule.getApproverType()) {
            case "DIRECT_SUPERIOR" -> {
                Long superior = org.superiorUserId(requesterId, time.today());
                if (superior != null && !superior.equals(requesterId)) {
                    t.setAssigneeUserId(superior);
                    t.setAssigneeLabel(rule.getLabel() + " · " + Objects.requireNonNullElse(users.name(superior), "?"));
                } else {
                    // Tidak ada atasan terdaftar: jatuh ke Manager aplikasi pemilik dokumen.
                    t.setAssigneeRole("MANAGER");
                    t.setAssigneeApp(type.getAppCode());
                    t.setAssigneeLabel("Manager " + type.getAppCode() + " (atasan tidak terdaftar)");
                }
            }
            case "ROLE" -> {
                t.setAssigneeRole(rule.getApproverRole());
                t.setAssigneeApp(rule.getApproverApp() == null ? type.getAppCode() : rule.getApproverApp());
                t.setAssigneeLabel(rule.getLabel());
            }
            case "USER" -> {
                t.setAssigneeUserId(rule.getApproverUserId());
                t.setAssigneeLabel(rule.getLabel() + " · " + Objects.requireNonNullElse(users.name(rule.getApproverUserId()), "?"));
            }
            default -> throw new BusinessException("APPROVAL_RULE", "Tipe approver tidak dikenal: " + rule.getApproverType());
        }
    }

    @Transactional
    public void approve(Long taskId, String reason, String password) {
        ApprovalTask t = decidable(taskId);
        signIfRequired(t, ESignatureService.Meaning.APPROVE, password, reason);
        CurrentUser me = UserContext.current();
        t.setStatus(ApprovalTask.Status.APPROVED);
        t.setDecidedBy(me.id());
        t.setDecidedAt(time.now());
        t.setDecisionReason(blankToNull(reason));
        activity.log(t.getDocType(), t.getDocId(), "APPROVAL",
                "Menyetujui level " + t.getLevel() + (reason == null || reason.isBlank() ? "" : ": " + reason));

        ApprovalTask next = tasks.findByDocTypeAndDocIdOrderByLevelAscIdAsc(t.getDocType(), t.getDocId()).stream()
                .filter(x -> x.getStatus() == ApprovalTask.Status.WAITING)
                .findFirst().orElse(null);
        if (next != null) {
            next.setStatus(ApprovalTask.Status.PENDING);
            next.setActivatedAt(time.now());
            events.publishEvent(new ApprovalEvents.ApprovalTaskActivated(next.getId()));
        } else {
            events.publishEvent(new ApprovalEvents.ApprovalCompleted(t.getDocType(), t.getDocId(), me.id()));
        }
    }

    @Transactional
    public void reject(Long taskId, String reason, String password) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("REASON_REQUIRED", "Alasan penolakan wajib diisi");
        }
        ApprovalTask t = decidable(taskId);
        signIfRequired(t, ESignatureService.Meaning.REVIEW, password, reason);
        CurrentUser me = UserContext.current();
        t.setStatus(ApprovalTask.Status.REJECTED);
        t.setDecidedBy(me.id());
        t.setDecidedAt(time.now());
        t.setDecisionReason(reason);
        cancelOpen(t.getDocType(), t.getDocId());
        activity.log(t.getDocType(), t.getDocId(), "APPROVAL", "Menolak level " + t.getLevel() + ": " + reason);
        events.publishEvent(new ApprovalEvents.ApprovalRejected(t.getDocType(), t.getDocId(), me.id(), reason));
    }

    /** Membatalkan semua level yang belum diputuskan (dokumen ditarik, dibatalkan, atau ditolak). */
    @Transactional
    public void cancelOpen(String docType, Long docId) {
        tasks.findByDocTypeAndDocIdOrderByLevelAscIdAsc(docType, docId).stream()
                .filter(x -> x.getStatus() == ApprovalTask.Status.WAITING || x.getStatus() == ApprovalTask.Status.PENDING)
                .forEach(x -> x.setStatus(ApprovalTask.Status.CANCELLED));
    }

    /** Pengguna terlibat sebagai approver (boleh melihat dokumen walau di luar aplikasinya). */
    public boolean involves(String docType, Long docId, CurrentUser me) {
        return tasks.findByDocTypeAndDocIdOrderByLevelAscIdAsc(docType, docId).stream().anyMatch(t ->
                me.id().equals(t.getAssigneeUserId()) || me.id().equals(t.getDecidedBy())
                        || (t.getAssigneeRole() != null && t.getStatus() == ApprovalTask.Status.PENDING
                        && perm.holdsRole(me, t.getAssigneeRole(), t.getAssigneeApp(), t.getPlantId())));
    }

    public List<TaskView> forDocument(String docType, Long docId) {
        return views(tasks.findByDocTypeAndDocIdOrderByLevelAscIdAsc(docType, docId));
    }

    /** ESS-10 Kotak Approval Saya: semua level PENDING yang boleh diputuskan pengguna. */
    public List<TaskView> inbox(String app) {
        CurrentUser me = UserContext.current();
        StringBuilder sql = new StringBuilder("""
                SELECT t.id FROM core.approval_task t
                WHERE t.status = 'PENDING' AND t.requested_by <> ?
                  AND NOT EXISTS (SELECT 1 FROM core.approval_task d
                                  WHERE d.doc_type = t.doc_type AND d.doc_id = t.doc_id AND d.decided_by = ?)
                  AND (t.assignee_user_id = ?""");
        List<Object> args = new ArrayList<>(List.of(me.id(), me.id(), me.id()));
        for (CurrentUser.Grant g : me.grants()) {
            sql.append(" OR (t.assignee_role = ? AND (t.assignee_app = '*' OR ? = '*' OR t.assignee_app = ?)");
            args.add(g.roleCode());
            args.add(g.appCode());
            args.add(g.appCode());
            if (g.plantId() != null) {
                sql.append(" AND t.plant_id = ?");
                args.add(g.plantId());
            }
            sql.append(")");
        }
        sql.append(")");
        if (app != null && !app.isBlank()) {
            sql.append(" AND t.app_code = ?");
            args.add(app);
        }
        sql.append(" ORDER BY t.activated_at");
        List<Long> ids = jdbc.queryForList(sql.toString(), Long.class, args.toArray());
        return ids.isEmpty() ? List.of() : views(tasks.findAllById(ids).stream()
                .sorted((a, b) -> a.getActivatedAt().compareTo(b.getActivatedAt())).toList());
    }

    private ApprovalTask decidable(Long taskId) {
        ApprovalTask t = tasks.findForUpdate(taskId).orElseThrow(() -> new NotFoundException("Tugas approval", taskId));
        if (t.getStatus() != ApprovalTask.Status.PENDING) {
            throw new BusinessException("APPROVAL_STATE", "Tugas approval ini sudah tidak menunggu keputusan");
        }
        CurrentUser me = UserContext.current();
        if (me.id().equals(t.getRequestedBy())) {
            throw new BusinessException("SOD", "Pemisahan tugas: pembuat dokumen tidak boleh menyetujui dokumennya sendiri");
        }
        boolean alreadyDecided = tasks.findByDocTypeAndDocIdOrderByLevelAscIdAsc(t.getDocType(), t.getDocId()).stream()
                .anyMatch(x -> me.id().equals(x.getDecidedBy()) && x.getStatus() == ApprovalTask.Status.APPROVED);
        if (alreadyDecided) {
            throw new BusinessException("SOD", "Pemisahan tugas: Anda sudah menyetujui level lain dokumen ini");
        }
        boolean allowed = me.id().equals(t.getAssigneeUserId())
                || (t.getAssigneeRole() != null && perm.holdsRole(me, t.getAssigneeRole(), t.getAssigneeApp(), t.getPlantId()));
        if (!allowed) {
            throw new BusinessException("NOT_APPROVER", "Anda bukan approver untuk level ini (" + t.getAssigneeLabel() + ")");
        }
        return t;
    }

    private void signIfRequired(ApprovalTask t, ESignatureService.Meaning meaning, String password, String reason) {
        boolean required = docTypes.findByCode(t.getDocType()).map(DocType::isRequiresEsign).orElse(false);
        if (required) {
            esign.sign(t.getDocType(), t.getDocId(), meaning, password, reason);
        }
    }

    private List<TaskView> views(List<ApprovalTask> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        list.forEach(t -> {
            ids.add(t.getAssigneeUserId());
            ids.add(t.getRequestedBy());
            ids.add(t.getDecidedBy());
        });
        Map<Long, String> names = users.names(ids);
        Map<String, DocType> types = new java.util.HashMap<>();
        return list.stream().map(t -> {
            DocType dt = types.computeIfAbsent(t.getDocType(), c -> docTypes.findByCode(c).orElse(null));
            return new TaskView(t.getId(), t.getDocType(), t.getDocId(), t.getDocNo(), t.getDocSummary(), t.getDocAmount(),
                    t.getAppCode(), dt == null ? null : dt.getMenuCode(), t.getPlantId(), t.getLevel(), t.getStatus().name(),
                    t.getAssigneeLabel(), t.getAssigneeUserId(), names.get(t.getAssigneeUserId()), t.getRequestedBy(),
                    names.get(t.getRequestedBy()), t.getActivatedAt(), t.getDecidedBy(), names.get(t.getDecidedBy()),
                    t.getDecidedAt(), t.getDecisionReason(), dt != null && dt.isRequiresEsign());
        }).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
