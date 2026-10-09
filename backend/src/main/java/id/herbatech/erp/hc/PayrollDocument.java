package id.herbatech.erp.hc;

import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

interface PayrollRunRepository extends DocumentRepository<PayrollRun> {
}

@Component
class PayrollHandler implements DocumentHandler<PayrollRun> {

    private final PayrollRunRepository repo;
    private final PayrollService payroll;

    PayrollHandler(PayrollRunRepository repo, PayrollService payroll) {
        this.repo = repo;
        this.payroll = payroll;
    }

    @Override public String docType() { return "PAYR"; }
    /** Tanggal payroll mengikuti kunci periode FIN (jurnal); kunci absensi HC adalah prasyarat hitung. */
    @Override public String periodModule() { return "FIN"; }
    @Override public PayrollRun load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Payroll", id)); }
    @Override public PayrollRun save(PayrollRun doc) { return repo.save(doc); }

    @Override
    public String summary(PayrollRun d) {
        return "Payroll %s · %d karyawan".formatted(PayrollService.period(d.getPeriod()), d.getEmployeeCount());
    }

    @Override public BigDecimal amount(PayrollRun d) { return d.getTotalGross(); }

    @Override
    public void validateSubmit(PayrollRun d) {
        if (d.getCalculatedAt() == null) {
            throw new BusinessException("PAYROLL_CALC", "Hitung payroll terlebih dahulu");
        }
        if (d.getWarnings() != null && d.getWarnings().contains("ERROR:")) {
            throw new BusinessException("PAYROLL_ERRORS", "Masih ada kesalahan data yang harus diperbaiki:\n" + d.getWarnings());
        }
    }

    @Override
    public void onPost(PayrollRun d) {
        payroll.post(d);
    }

    @Override
    public PayrollRun reverse(PayrollRun d, LocalDate date, String reason) {
        payroll.reverse(d, date, reason);
        return d;
    }
}

/** HC-09 API payroll, slip, dan data gaji karyawan. */
@RestController
@RequestMapping("/api/hc/payroll-runs")
class PayrollController extends DocumentApi<PayrollRun> {

    private final PayrollService payroll;
    private final PayrollHandler handler;

    PayrollController(PayrollHandler handler, PayrollRunRepository repo, Support support, PayrollService payroll) {
        super(handler, repo, support, PayrollRun.class);
        this.payroll = payroll;
        this.handler = handler;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "period", "description");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("employeeCount", "totalGross", "totalDeduction", "totalNet", "totalEmployer", "totalPph21",
                "calculatedAt", "warnings");
    }

    @Override
    protected void beforeSave(PayrollRun d, boolean isNew) {
        payroll.requirePayrollAccess();
        PayrollService.period(d.getPeriod());
        if (!isNew) {
            d.setCalculatedAt(null);
        }
        if (d.getDescription() == null || d.getDescription().isBlank()) {
            d.setDescription("Payroll " + PayrollService.period(d.getPeriod()));
        }
    }

    @PostMapping("/{id}/calculate")
    @Transactional
    public Envelope calculate(@PathVariable Long id) {
        PayrollRun run = handler.load(id);
        payroll.calculate(run);
        handler.save(run);
        return envelope(run);
    }

    /** Daftar slip per karyawan — data gaji, hanya peran Payroll & Direktur. */
    @GetMapping("/{id}/slips")
    @Transactional(readOnly = true)
    public List<PayrollService.SlipRow> slips(@PathVariable Long id) {
        payroll.requirePayrollAccess();
        return payroll.slips(id);
    }
}

@RestController
@RequestMapping("/api/hc")
class PayslipController {

    private final PayrollService payroll;
    private final PermissionService perm;
    private final HcSupport hc;
    private final JdbcTemplate jdbc;

    PayslipController(PayrollService payroll, PermissionService perm, HcSupport hc, JdbcTemplate jdbc) {
        this.payroll = payroll;
        this.perm = perm;
        this.hc = hc;
        this.jdbc = jdbc;
    }

    /** ESS-03 slip gaji saya (hanya payroll yang sudah diposting). */
    @GetMapping("/my-slips")
    public List<PayrollService.SlipRow> mySlips() {
        return payroll.mySlips(hc.myEmployeeId());
    }

    @GetMapping("/payroll-slips/{slipId}")
    public PayrollService.Slip slip(@PathVariable Long slipId) {
        PayrollService.Slip s = payroll.slip(slipId);
        boolean own = s.slip().employeeId().equals(UserContext.current().employeeId())
                && ("POSTED".equals(s.slip().runStatus()) || "DONE".equals(s.slip().runStatus()));
        if (!own) {
            perm.require(PayrollService.MENU, Action.PAYROLL);
        }
        return s;
    }

    // ------------------------------------------------------------------ data gaji karyawan

    record SalaryRow(Long componentId, String code, String name, String kind, boolean fixed, BigDecimal amount) {
    }

    record SalaryInput(@NotNull Long componentId, @NotNull @PositiveOrZero BigDecimal amount) {
    }

    @GetMapping("/salaries")
    public List<SalaryRow> salaries(@RequestParam Long employeeId) {
        perm.require(PayrollService.MENU, Action.PAYROLL);
        return jdbc.query("""
                SELECT c.id, c.code, c.name, c.kind, c.fixed, s.amount FROM hc.salary_component c
                LEFT JOIN hc.employee_salary s ON s.component_id = c.id AND s.employee_id = ? AND s.active
                WHERE c.active AND NOT c.system AND c.kind = 'EARNING' ORDER BY c.seq""",
                (rs, i) -> new SalaryRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5),
                        rs.getBigDecimal(6)), employeeId);
    }

    /** Simpan data gaji (perubahan tercatat di audit trail lewat tabel hc.employee_salary). */
    @PutMapping("/salaries")
    @Transactional
    public List<SalaryRow> saveSalaries(@RequestParam Long employeeId, @Valid @RequestBody List<SalaryInput> rows) {
        perm.require(PayrollService.MENU, Action.PAYROLL);
        hc.requireActiveEmployee(employeeId);
        for (SalaryInput r : rows) {
            Boolean system = jdbc.queryForList("SELECT system FROM hc.salary_component WHERE id = ?", Boolean.class, r.componentId())
                    .stream().findFirst().orElseThrow(() -> new BusinessException("COMPONENT", "Komponen gaji tidak dikenal"));
            if (system) {
                throw new BusinessException("COMPONENT", "Komponen sistem dihitung otomatis");
            }
            jdbc.update("""
                    INSERT INTO hc.employee_salary (employee_id, component_id, amount, created_by) VALUES (?, ?, ?, ?)
                    ON CONFLICT (employee_id, component_id) DO UPDATE SET amount = EXCLUDED.amount, active = TRUE,
                        updated_at = now(), updated_by = EXCLUDED.created_by, version = hc.employee_salary.version + 1""",
                    employeeId, r.componentId(), r.amount(), UserContext.userId());
            jdbc.update("""
                    INSERT INTO core.audit_log (table_name, record_id, action, field, new_value, user_id, username)
                    VALUES ('hc.employee_salary', ?, 'UPDATE', ?, '********', ?, ?)""",
                    employeeId + ":" + r.componentId(), "amount", UserContext.userId(), UserContext.current().username());
        }
        return salaries(employeeId);
    }
}
