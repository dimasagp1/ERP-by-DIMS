package id.herbatech.erp.fin;

import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.document.DocumentWorkflowService;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

interface BudgetRepository extends DocumentRepository<Budget> {
}

@Component
class BudgetHandler implements DocumentHandler<Budget> {

    private final BudgetRepository repo;
    private final FinSupport fin;
    private final JdbcTemplate jdbc;
    private final DocumentWorkflowService workflow;

    BudgetHandler(BudgetRepository repo, FinSupport fin, JdbcTemplate jdbc, @Lazy DocumentWorkflowService workflow) {
        this.repo = repo;
        this.fin = fin;
        this.jdbc = jdbc;
        this.workflow = workflow;
    }

    @Override public String docType() { return "BGT"; }
    @Override public String periodModule() { return "FIN"; }
    @Override public Budget load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Anggaran", id)); }
    @Override public Budget save(Budget doc) { return repo.save(doc); }

    @Override
    public String summary(Budget d) {
        return "Anggaran %s %d rev. %d · %s".formatted(d.getKind(), d.getYear(), d.getRevision(), d.getDescription());
    }

    @Override public BigDecimal amount(Budget d) { return d.getTotal(); }

    @Override
    public void validateSubmit(Budget d) {
        if (d.getLines().isEmpty()) {
            throw new BusinessException("BGT_LINES", "Isi minimal satu baris anggaran");
        }
        Set<String> seen = new HashSet<>();
        for (Budget.Line l : d.getLines()) {
            if (!seen.add(l.getCostCenterId() + ":" + l.getAccountId())) {
                throw new BusinessException("BGT_DUP", "Kombinasi cost center × akun muncul lebih dari sekali (baris " + l.getLineNo() + ")");
            }
            fin.validateAccount(l.getAccountId(), l.getCostCenterId(), "Baris " + l.getLineNo());
            if (l.getCostCenterId() == null) {
                throw new BusinessException("BGT_CC", "Baris " + l.getLineNo() + ": cost center wajib");
            }
        }
    }

    /** Revisi yang diposting menggantikan anggaran berlaku sebelumnya (status Selesai). */
    @Override
    public void onPost(Budget d) {
        for (Long old : jdbc.queryForList("""
                SELECT id FROM fin.budget WHERE plant_id = ? AND year = ? AND kind = ? AND status = 'POSTED' AND id <> ?""",
                Long.class, d.getPlantId(), d.getYear(), d.getKind(), d.getId())) {
            workflow.markDone("BGT", old, "Digantikan " + d.getDocNo());
        }
    }
}

@RestController
@RequestMapping("/api/fin/budgets")
class BudgetController extends DocumentApi<Budget> {

    private final JdbcTemplate jdbc;

    BudgetController(BudgetHandler handler, BudgetRepository repo, Support support, JdbcTemplate jdbc) {
        super(handler, repo, support, Budget.class);
        this.jdbc = jdbc;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "description");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("total", "revision");
    }

    @Override
    protected void apply(Budget target, Budget in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (Budget.Line l : in.getLines()) {
            l.setId(null);
            l.setBudget(target);
            l.setLineNo(no++);
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(Budget d, boolean isNew) {
        require(d.getYear() >= 2000 && d.getYear() <= 2100, "BGT_YEAR", "Tahun anggaran tidak valid");
        require(List.of("OPEX", "CAPEX").contains(d.getKind()), "BGT_KIND", "Jenis anggaran OPEX/CAPEX");
        require(d.getDescription() != null && !d.getDescription().isBlank(), "BGT_DESC", "Uraian wajib diisi");
        if (isNew) {
            Short rev = jdbc.queryForObject("""
                    SELECT COALESCE(MAX(revision) + 1, 0)::smallint FROM fin.budget WHERE year = ? AND kind = ? AND status <> 'CANCELLED'""",
                    Short.class, d.getYear(), d.getKind());
            d.setRevision(rev == null ? 0 : rev);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Budget.Line l : d.getLines()) {
            for (String m : List.of("m01", "m02", "m03", "m04", "m05", "m06", "m07", "m08", "m09", "m10", "m11", "m12")) {
                org.springframework.beans.BeanWrapperImpl w = new org.springframework.beans.BeanWrapperImpl(l);
                if (w.getPropertyValue(m) == null) {
                    w.setPropertyValue(m, BigDecimal.ZERO);
                }
            }
            BigDecimal t = l.months().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            require(l.months().stream().allMatch(v -> v.signum() >= 0), "BGT_NEG", "Nilai anggaran tidak boleh negatif");
            l.setTotal(t);
            total = total.add(t);
        }
        d.setTotal(total);
    }
}
