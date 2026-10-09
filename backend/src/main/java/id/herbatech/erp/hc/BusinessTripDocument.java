package id.herbatech.erp.hc;

import id.herbatech.erp.fin.CashAdvanceService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

interface BusinessTripRepository extends DocumentRepository<BusinessTrip> {
}

@Component
class BusinessTripHandler implements DocumentHandler<BusinessTrip> {

    private final BusinessTripRepository repo;
    private final AttendanceService attendance;
    private final CashAdvanceService advances;
    private final HcSupport hc;
    private final JdbcTemplate jdbc;

    BusinessTripHandler(BusinessTripRepository repo, AttendanceService attendance, CashAdvanceService advances,
                        HcSupport hc, JdbcTemplate jdbc) {
        this.repo = repo;
        this.attendance = attendance;
        this.advances = advances;
        this.hc = hc;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "SPD"; }
    @Override public String periodModule() { return "HC"; }
    @Override public BusinessTrip load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("SPD", id)); }
    @Override public BusinessTrip save(BusinessTrip doc) { return repo.save(doc); }

    @Override
    public String summary(BusinessTrip d) {
        return "SPD %s · %s · %s s.d. %s%s".formatted(hc.employeeName(d.getEmployeeId()), d.getDestination(),
                d.getStartDate(), d.getEndDate(), d.isAbroad() ? " · luar negeri" : "");
    }

    @Override
    public BigDecimal amount(BusinessTrip d) {
        return d.getAdvanceAmount().add(d.getPerDiem());
    }

    @Override
    public Long requesterId(BusinessTrip d) {
        Long u = hc.userOfEmployee(d.getEmployeeId());
        return u != null ? u : d.getCreatedBy();
    }

    /** PRD §13: SPD luar negeri butuh approval Manager HC di level 2. */
    @Override
    public Set<String> approvalFlags(BusinessTrip d) {
        return d.isAbroad() ? Set.of("ABROAD") : Set.of();
    }

    @Override
    public void validateSubmit(BusinessTrip d) {
        hc.requireActiveEmployee(d.getEmployeeId());
        if (d.getDestination() == null || d.getDestination().isBlank() || d.getPurpose() == null || d.getPurpose().isBlank()) {
            throw new BusinessException("SPD_REQ", "Tujuan dan keperluan perjalanan wajib diisi");
        }
        if (d.getAdvanceAmount().signum() < 0 || d.getPerDiem().signum() < 0) {
            throw new BusinessException("SPD_AMOUNT", "Nilai uang muka/uang harian tidak boleh negatif");
        }
        Long overlap = jdbc.queryForObject("""
                SELECT count(*) FROM hc.business_trip WHERE employee_id = ? AND id <> ?
                  AND status IN ('SUBMITTED','APPROVED','POSTED','DONE') AND start_date <= ? AND end_date >= ?""",
                Long.class, d.getEmployeeId(), d.getId(), java.sql.Date.valueOf(d.getEndDate()), java.sql.Date.valueOf(d.getStartDate()));
        if (overlap != null && overlap > 0) {
            throw new BusinessException("SPD_OVERLAP", "Tanggal bertumpuk dengan SPD lain yang masih berlaku");
        }
    }

    /** Disetujui → absensi DINAS; uang muka menjadi draft uang muka kerja di FIN-30 (PRD ESS-09 → FIN-30). */
    @Override
    public void onApproved(BusinessTrip d) {
        attendance.applyStatus(d.getEmployeeId(), d.getStartDate(), d.getEndDate(), "DINAS", "SPD", d.getDocNo());
        if (d.getAdvanceAmount().signum() > 0) {
            String no = advances.createAdvanceDraft(d.getPlantId(), d.getEmployeeId(), d.getAdvanceAmount(),
                    "Uang muka SPD " + d.getDocNo() + " · " + d.getDestination(), d.getDocNo());
            d.setAdvanceDocNo(no);
        }
    }

    @Override
    public void onCancel(BusinessTrip d) {
        if (d.getStatus() == DocStatus.APPROVED) {
            attendance.removeBySource(d.getDocNo());
        }
    }
}

/** HC-08 & ESS-09: API SPD. */
@RestController
@RequestMapping("/api/hc/trips")
class BusinessTripController extends DocumentApi<BusinessTrip> {

    private final HcSupport hc;
    private final JdbcTemplate jdbc;

    BusinessTripController(BusinessTripHandler handler, BusinessTripRepository repo, Support support, HcSupport hc, JdbcTemplate jdbc) {
        super(handler, repo, support, BusinessTrip.class);
        this.hc = hc;
        this.jdbc = jdbc;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "destination", "purpose");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("advanceDocNo");
    }

    @Override
    protected void beforeSave(BusinessTrip d, boolean isNew) {
        if (!hc.actsForOthers("HC-08") || d.getEmployeeId() == null) {
            d.setEmployeeId(hc.myEmployeeId());
        }
        require(d.getStartDate() != null && d.getEndDate() != null, "SPD_DATE", "Tanggal berangkat & kembali wajib diisi");
        require(!d.getEndDate().isBefore(d.getStartDate()), "SPD_DATE", "Tanggal kembali tidak boleh sebelum berangkat");
        require(List.of("DARAT", "UDARA", "LAUT").contains(d.getTransport()), "SPD_TRANSPORT", "Moda transportasi tidak dikenal");
        if (d.getCostCenterId() == null) {
            d.setCostCenterId(jdbc.queryForList("SELECT cost_center_id FROM hc.employee WHERE id = ?", Long.class, d.getEmployeeId())
                    .stream().findFirst().orElse(null));
        }
        if (d.getAdvanceAmount() == null) {
            d.setAdvanceAmount(BigDecimal.ZERO);
        }
        if (d.getPerDiem() == null) {
            d.setPerDiem(BigDecimal.ZERO);
        }
    }

    @Override
    protected Predicate mine(Root<BusinessTrip> root, CriteriaQuery<?> q, CriteriaBuilder cb, CurrentUser me) {
        Long emp = me.employeeId();
        return emp == null ? cb.equal(root.get("createdBy"), me.id())
                : cb.or(cb.equal(root.get("createdBy"), me.id()), cb.equal(root.get("employeeId"), emp));
    }

    @Override
    protected void enrich(Map<String, Object> body, BusinessTrip d) {
        body.put("employeeName", hc.employeeName(d.getEmployeeId()));
    }
}
