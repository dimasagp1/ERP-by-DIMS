package id.herbatech.erp.hc;

import id.herbatech.erp.shared.attachment.AttachmentService;
import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.domain.DocStatus;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.security.CurrentUser;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

interface LeaveRequestRepository extends DocumentRepository<LeaveRequest> {
}

/** Saldo cuti tahunan: hak (HC-08) − cuti tahunan disetujui/diajukan pada tahun berjalan. */
@Service
class LeaveBalanceService {

    record Balance(int year, BigDecimal entitlement, BigDecimal used, BigDecimal pending, BigDecimal remaining) {
    }

    private final JdbcTemplate jdbc;

    LeaveBalanceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Balance balance(Long employeeId, int year, Long excludeDocId) {
        BigDecimal ent = jdbc.queryForList("SELECT days + carried_over FROM hc.leave_entitlement WHERE employee_id = ? AND year = ? AND active",
                BigDecimal.class, employeeId, year).stream().findFirst().orElseGet(() -> new BigDecimal(
                jdbc.queryForObject("SELECT value FROM hc.payroll_param WHERE key = 'ANNUAL_LEAVE'", String.class)));
        Map<String, Object> r = jdbc.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN l.status IN ('APPROVED','POSTED','DONE') THEN l.days END), 0) AS used,
                       COALESCE(SUM(CASE WHEN l.status = 'SUBMITTED' THEN l.days END), 0) AS pending
                FROM hc.leave_request l JOIN hc.leave_type t ON t.id = l.leave_type_id
                WHERE l.employee_id = ? AND t.deducts_annual AND EXTRACT(YEAR FROM l.start_date) = ? AND l.id <> ?""",
                employeeId, year, excludeDocId == null ? -1L : excludeDocId);
        BigDecimal used = (BigDecimal) r.get("used");
        BigDecimal pending = (BigDecimal) r.get("pending");
        return new Balance(year, ent, used, pending, ent.subtract(used).subtract(pending));
    }
}

@Component
class LeaveHandler implements DocumentHandler<LeaveRequest> {

    private final LeaveRequestRepository repo;
    private final AttendanceService attendance;
    private final LeaveBalanceService balances;
    private final AttachmentService attachments;
    private final HcSupport hc;
    private final JdbcTemplate jdbc;

    LeaveHandler(LeaveRequestRepository repo, AttendanceService attendance, LeaveBalanceService balances,
                 AttachmentService attachments, HcSupport hc, JdbcTemplate jdbc) {
        this.repo = repo;
        this.attendance = attendance;
        this.balances = balances;
        this.attachments = attachments;
        this.hc = hc;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "LV"; }
    @Override public String periodModule() { return "HC"; }
    @Override public LeaveRequest load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Pengajuan cuti", id)); }
    @Override public LeaveRequest save(LeaveRequest doc) { return repo.save(doc); }

    @Override
    public String summary(LeaveRequest d) {
        String type = jdbc.queryForList("SELECT name FROM hc.leave_type WHERE id = ?", String.class, d.getLeaveTypeId())
                .stream().findFirst().orElse("Cuti");
        return "%s · %s · %s s.d. %s (%s hari)".formatted(type, hc.employeeName(d.getEmployeeId()),
                d.getStartDate(), d.getEndDate(), d.getDays().stripTrailingZeros().toPlainString());
    }

    @Override
    public Long requesterId(LeaveRequest d) {
        Long u = hc.userOfEmployee(d.getEmployeeId());
        return u != null ? u : d.getCreatedBy();
    }

    @Override
    public void validateSubmit(LeaveRequest d) {
        Map<String, Object> type = jdbc.queryForMap("SELECT * FROM hc.leave_type WHERE id = ?", d.getLeaveTypeId());
        hc.requireActiveEmployee(d.getEmployeeId());
        if (d.getDays().signum() <= 0) {
            throw new BusinessException("LV_DAYS", "Rentang tanggal tidak mencakup hari kerja");
        }
        Integer max = (Integer) type.get("max_days");
        if (max != null && d.getDays().compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new BusinessException("LV_MAX", type.get("name") + " maksimal " + max + " hari");
        }
        Long overlap = jdbc.queryForObject("""
                SELECT count(*) FROM hc.leave_request WHERE employee_id = ? AND id <> ?
                  AND status IN ('SUBMITTED','APPROVED','POSTED','DONE') AND start_date <= ? AND end_date >= ?""",
                Long.class, d.getEmployeeId(), d.getId(), Date.valueOf(d.getEndDate()), Date.valueOf(d.getStartDate()));
        if (overlap != null && overlap > 0) {
            throw new BusinessException("LV_OVERLAP", "Tanggal bertumpuk dengan pengajuan cuti lain yang masih berlaku");
        }
        if ((Boolean) type.get("deducts_annual")) {
            LeaveBalanceService.Balance b = balances.balance(d.getEmployeeId(), d.getStartDate().getYear(), d.getId());
            if (b.remaining().compareTo(d.getDays()) < 0) {
                throw BusinessException.of("LV_BALANCE", "Sisa cuti tahunan %s hari, tidak cukup untuk %s hari",
                        b.remaining().stripTrailingZeros().toPlainString(), d.getDays().stripTrailingZeros().toPlainString());
            }
        }
        if ((Boolean) type.get("requires_attachment") && attachments.list("LV", d.getId()).isEmpty()) {
            throw new BusinessException("LV_ATTACHMENT", type.get("name") + " wajib melampirkan dokumen pendukung (mis. surat dokter)");
        }
        attendance.workingDays(d.getStartDate(), d.getEndDate()).forEach(attendance::assertOpen);
    }

    @Override
    public void onApproved(LeaveRequest d) {
        String status = jdbc.queryForObject("SELECT attendance_status FROM hc.leave_type WHERE id = ?", String.class, d.getLeaveTypeId());
        attendance.applyStatus(d.getEmployeeId(), d.getStartDate(), d.getEndDate(), status, "CUTI", d.getDocNo());
    }

    @Override
    public void onCancel(LeaveRequest d) {
        if (d.getStatus() == DocStatus.APPROVED) {
            attendance.removeBySource(d.getDocNo());
        }
    }
}

/** HC-08 & ESS-01: API pengajuan cuti. */
@RestController
@RequestMapping("/api/hc/leaves")
class LeaveController extends DocumentApi<LeaveRequest> {

    private final HcSupport hc;
    private final AttendanceService attendance;
    private final LeaveBalanceService balances;
    private final JdbcTemplate jdbc;

    LeaveController(LeaveHandler handler, LeaveRequestRepository repo, Support support, HcSupport hc,
                    AttendanceService attendance, LeaveBalanceService balances, JdbcTemplate jdbc) {
        super(handler, repo, support, LeaveRequest.class);
        this.hc = hc;
        this.attendance = attendance;
        this.balances = balances;
        this.jdbc = jdbc;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "reason");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("days");
    }

    @Override
    protected void beforeSave(LeaveRequest d, boolean isNew) {
        if (!hc.actsForOthers("HC-08") || d.getEmployeeId() == null) {
            d.setEmployeeId(hc.myEmployeeId());
        }
        require(d.getLeaveTypeId() != null, "LV_TYPE", "Jenis cuti wajib dipilih");
        require(d.getStartDate() != null && d.getEndDate() != null, "LV_DATE", "Tanggal mulai dan selesai wajib diisi");
        require(!d.getEndDate().isBefore(d.getStartDate()), "LV_DATE", "Tanggal selesai tidak boleh sebelum tanggal mulai");
        d.setDays(BigDecimal.valueOf(attendance.workingDays(d.getStartDate(), d.getEndDate()).size()));
    }

    @Override
    protected Predicate mine(Root<LeaveRequest> root, CriteriaQuery<?> q, CriteriaBuilder cb, CurrentUser me) {
        Long emp = me.employeeId();
        return emp == null ? cb.equal(root.get("createdBy"), me.id())
                : cb.or(cb.equal(root.get("createdBy"), me.id()), cb.equal(root.get("employeeId"), emp));
    }

    @Override
    protected void enrich(Map<String, Object> body, LeaveRequest d) {
        body.put("employeeName", hc.employeeName(d.getEmployeeId()));
        body.put("leaveTypeName", jdbc.queryForList("SELECT name FROM hc.leave_type WHERE id = ?", String.class, d.getLeaveTypeId())
                .stream().findFirst().orElse(null));
    }

    /** Saldo cuti tahunan (ESS-01 kartu "Sisa cuti"). */
    @GetMapping("/balance")
    public LeaveBalanceService.Balance balance(@RequestParam(required = false) Long employeeId,
                                               @RequestParam(required = false) Integer year) {
        Long emp = employeeId != null && hc.actsForOthers("HC-08") ? employeeId : hc.myEmployeeId();
        return balances.balance(emp, year != null ? year : LocalDate.now(java.time.ZoneId.of("Asia/Jakarta")).getYear(), null);
    }
}
