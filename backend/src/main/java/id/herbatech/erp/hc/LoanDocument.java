package id.herbatech.erp.hc;

import id.herbatech.erp.fin.JournalPostingService;
import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

interface EmployeeLoanRepository extends DocumentRepository<EmployeeLoan> {
}

@Component
class LoanHandler implements DocumentHandler<EmployeeLoan> {

    private final EmployeeLoanRepository repo;
    private final JournalPostingService journals;
    private final HcSupport hc;
    private final JdbcTemplate jdbc;

    LoanHandler(EmployeeLoanRepository repo, JournalPostingService journals, HcSupport hc, JdbcTemplate jdbc) {
        this.repo = repo;
        this.journals = journals;
        this.hc = hc;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "LOAN"; }
    @Override public String periodModule() { return "FIN"; }
    @Override public EmployeeLoan load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Pinjaman", id)); }
    @Override public EmployeeLoan save(EmployeeLoan doc) { return repo.save(doc); }

    @Override
    public String summary(EmployeeLoan d) {
        return "%s %s · %d× cicilan".formatted("KASBON".equals(d.getKind()) ? "Kasbon" : "Pinjaman",
                hc.employeeName(d.getEmployeeId()), d.getInstallments());
    }

    @Override public BigDecimal amount(EmployeeLoan d) { return d.getPrincipal(); }

    @Override
    public Long requesterId(EmployeeLoan d) {
        Long u = hc.userOfEmployee(d.getEmployeeId());
        return u != null ? u : d.getCreatedBy();
    }

    @Override
    public void validateSubmit(EmployeeLoan d) {
        hc.requireActiveEmployee(d.getEmployeeId());
        BigDecimal outstanding = jdbc.queryForObject("""
                SELECT COALESCE(SUM(principal - repaid_amount), 0) FROM hc.employee_loan
                WHERE employee_id = ? AND id <> ? AND status IN ('SUBMITTED','APPROVED','POSTED')""",
                BigDecimal.class, d.getEmployeeId(), d.getId());
        if (outstanding.signum() > 0 && "PINJAMAN".equals(d.getKind())) {
            throw BusinessException.of("LOAN_OUTSTANDING", "Masih ada pinjaman berjalan Rp %,.0f; lunasi dulu sebelum mengajukan pinjaman baru",
                    outstanding);
        }
    }

    /** Pencairan: piutang karyawan bertambah, bank berkurang. */
    @Override
    public void onPost(EmployeeLoan d) {
        String desc = "Pencairan " + summary(d);
        journals.postIds(d.getPlantId(), d.getDocDate(), List.of(
                new JournalPostingService.IdLine(journals.accountId("1240"), null, desc, d.getPrincipal(), null),
                new JournalPostingService.IdLine(journals.accountId("1111"), null, desc, null, d.getPrincipal())),
                "LOAN", d.getId(), d.getDocNo(), desc);
    }

    @Override
    public EmployeeLoan reverse(EmployeeLoan d, LocalDate date, String reason) {
        if (d.getRepaidAmount().signum() > 0) {
            throw new BusinessException("LOAN_REPAID", "Pinjaman yang sudah dicicil lewat payroll tidak bisa dibalik");
        }
        journals.reverseFor("LOAN", d.getId(), date, reason);
        return d;
    }
}

/** HC-14 API pinjaman & kasbon. */
@RestController
@RequestMapping("/api/hc/loans")
class LoanController extends DocumentApi<EmployeeLoan> {

    private final HcSupport hc;

    LoanController(LoanHandler handler, EmployeeLoanRepository repo, Support support, HcSupport hc) {
        super(handler, repo, support, EmployeeLoan.class);
        this.hc = hc;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "purpose");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("installmentAmount", "repaidAmount");
    }

    @Override
    protected void beforeSave(EmployeeLoan d, boolean isNew) {
        if (!hc.actsForOthers("HC-14") || d.getEmployeeId() == null) {
            d.setEmployeeId(hc.myEmployeeId());
        }
        require(List.of("PINJAMAN", "KASBON").contains(d.getKind()), "LOAN_KIND", "Jenis pinjaman tidak dikenal");
        require(d.getPrincipal() != null && d.getPrincipal().signum() > 0, "LOAN_AMOUNT", "Nilai pinjaman harus lebih dari nol");
        if ("KASBON".equals(d.getKind())) {
            d.setInstallments(1);
        }
        require(d.getInstallments() >= 1 && d.getInstallments() <= 60, "LOAN_INST", "Jumlah cicilan 1–60 kali");
        PayrollService.period(d.getStartPeriod());
        d.setInstallmentAmount(d.getPrincipal().divide(BigDecimal.valueOf(d.getInstallments()), 0, RoundingMode.CEILING));
    }

    @Override
    protected Predicate mine(Root<EmployeeLoan> root, CriteriaQuery<?> q, CriteriaBuilder cb, CurrentUser me) {
        Long emp = me.employeeId();
        return emp == null ? cb.equal(root.get("createdBy"), me.id())
                : cb.or(cb.equal(root.get("createdBy"), me.id()), cb.equal(root.get("employeeId"), emp));
    }

    @Override
    protected void enrich(Map<String, Object> body, EmployeeLoan d) {
        body.put("employeeName", hc.employeeName(d.getEmployeeId()));
        body.put("outstanding", d.getPrincipal().subtract(d.getRepaidAmount()));
    }
}
