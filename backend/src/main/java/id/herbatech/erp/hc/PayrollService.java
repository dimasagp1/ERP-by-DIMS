package id.herbatech.erp.hc;

import id.herbatech.erp.fin.JournalPostingService;
import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.domain.DocStatus;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HC-09 Penggajian: hitung slip dari absensi terkunci, lembur yang disetujui, data gaji, BPJS, PPh 21, dan cicilan
 * pinjaman; posting menjadi jurnal beban per cost center (PRD HC aturan 3 & 4).
 */
@Service
public class PayrollService {

    static final String MENU = "HC-09";
    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    private final JdbcTemplate jdbc;
    private final AttendanceService attendance;
    private final PermissionService perm;
    private final JournalPostingService journals;
    private final TimeService time;

    PayrollService(JdbcTemplate jdbc, AttendanceService attendance, PermissionService perm, JournalPostingService journals,
                   TimeService time) {
        this.jdbc = jdbc;
        this.attendance = attendance;
        this.perm = perm;
        this.journals = journals;
        this.time = time;
    }

    void requirePayrollAccess() {
        perm.require(MENU, Action.PAYROLL);
    }

    static YearMonth period(String yyyymm) {
        try {
            return YearMonth.parse(yyyymm, YYYYMM);
        } catch (RuntimeException e) {
            throw new BusinessException("PERIOD", "Periode harus berformat YYYYMM");
        }
    }

    /** Menghitung ulang semua slip untuk payroll berstatus Draft/Ditolak. */
    @Transactional
    public void calculate(PayrollRun run) {
        requirePayrollAccess();
        if (!run.getStatus().isEditable()) {
            throw new BusinessException("PAYROLL_STATE", "Payroll yang sudah diajukan tidak bisa dihitung ulang");
        }
        YearMonth ym = period(run.getPeriod());
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        if (!attendance.isLocked(start)) {
            throw new BusinessException("ATT_NOT_LOCKED", "Kunci absensi periode " + ym + " di HC-07 sebelum menghitung payroll (PRD HC aturan 3)");
        }
        jdbc.update("DELETE FROM hc.payroll_slip WHERE run_id = ?", run.getId());

        Map<String, String> params = new HashMap<>();
        jdbc.query("SELECT key, value FROM hc.payroll_param WHERE active", rs -> {
            params.put(rs.getString(1), rs.getString(2));
        });
        PayrollCalculator.Params p = new PayrollCalculator.Params(num(params, "BPJSKES_RATE_ER"), num(params, "BPJSKES_RATE_EE"),
                num(params, "BPJSKES_CAP"), num(params, "JHT_RATE_ER"), num(params, "JHT_RATE_EE"), num(params, "JP_RATE_ER"),
                num(params, "JP_RATE_EE"), num(params, "JP_CAP"), num(params, "JKK_RATE"), num(params, "JKM_RATE"),
                num(params, "OT_DIVISOR"), num(params, "JOB_EXPENSE_RATE"), num(params, "JOB_EXPENSE_MAX"),
                Boolean.parseBoolean(params.getOrDefault("DEDUCT_ALPA", "true")));
        Map<String, PayrollCalculator.ComponentDef> defs = new LinkedHashMap<>();
        jdbc.query("SELECT * FROM hc.salary_component WHERE active ORDER BY seq", rs -> {
            defs.put(rs.getString("code"), new PayrollCalculator.ComponentDef(rs.getString("code"), rs.getString("name"),
                    rs.getString("kind"), rs.getBoolean("fixed"), rs.getBoolean("taxable"), rs.getString("account_code"), rs.getInt("seq")));
        });
        Map<String, List<PayrollCalculator.TerBracket>> ter = new HashMap<>();
        jdbc.query("SELECT category, upper_limit, rate FROM hc.pph21_ter WHERE active", rs -> {
            ter.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                    .add(new PayrollCalculator.TerBracket(rs.getBigDecimal(2), rs.getBigDecimal(3)));
        });
        Map<String, Object[]> ptkp = new HashMap<>();
        jdbc.query("SELECT status, amount, ter_category FROM hc.ptkp WHERE active", rs -> {
            ptkp.put(rs.getString(1), new Object[]{rs.getBigDecimal(2), rs.getString(3)});
        });
        List<LocalDate> workDays = attendance.workingDays(start, end);
        boolean december = ym.getMonthValue() == 12;

        List<Map<String, Object>> employees = jdbc.queryForList("""
                SELECT e.id, e.nik, e.name, e.ptkp_status, e.cost_center_id, e.join_date, e.end_date FROM hc.employee e
                WHERE e.plant_id = ? AND e.join_date <= ? AND (e.end_date IS NULL OR e.end_date >= ?)
                  AND EXISTS (SELECT 1 FROM hc.employee_salary s JOIN hc.salary_component c ON c.id = s.component_id
                              WHERE s.employee_id = e.id AND s.active AND c.code = 'GAPOK' AND s.amount > 0)
                ORDER BY e.nik""", run.getPlantId(), Date.valueOf(end), Date.valueOf(start));

        List<String> warnings = new ArrayList<>();
        BigDecimal tGross = BigDecimal.ZERO, tDed = BigDecimal.ZERO, tNet = BigDecimal.ZERO, tEmp = BigDecimal.ZERO, tPph = BigDecimal.ZERO;
        int count = 0;
        for (Map<String, Object> e : employees) {
            Long empId = ((Number) e.get("id")).longValue();
            String label = e.get("nik") + " " + e.get("name");
            Object[] pt = ptkp.get((String) e.get("ptkp_status"));
            if (pt == null) {
                warnings.add("ERROR: " + label + " — status PTKP " + e.get("ptkp_status") + " tidak dikenal");
                continue;
            }
            LocalDate join = ((Date) e.get("join_date")).toLocalDate();
            LocalDate leave = e.get("end_date") == null ? null : ((Date) e.get("end_date")).toLocalDate();
            int payable = (int) workDays.stream().filter(d -> !d.isBefore(join) && (leave == null || !d.isAfter(leave))).count();
            int absent = jdbc.queryForObject("""
                    SELECT count(*) FROM hc.attendance WHERE employee_id = ? AND work_date BETWEEN ? AND ? AND status IN ('ALPA','IZIN')""",
                    Integer.class, empId, Date.valueOf(start), Date.valueOf(end));
            int present = jdbc.queryForObject("""
                    SELECT count(*) FROM hc.attendance WHERE employee_id = ? AND work_date BETWEEN ? AND ? AND status = 'HADIR'""",
                    Integer.class, empId, Date.valueOf(start), Date.valueOf(end));
            Map<String, BigDecimal> salary = new LinkedHashMap<>();
            jdbc.query("""
                    SELECT c.code, s.amount FROM hc.employee_salary s JOIN hc.salary_component c ON c.id = s.component_id
                    WHERE s.employee_id = ? AND s.active AND c.active AND NOT c.system""", rs -> {
                salary.put(rs.getString(1), rs.getBigDecimal(2));
            }, empId);
            List<PayrollCalculator.Overtime> ot = jdbc.query("""
                    SELECT r.day_type, COALESCE(l.actual_hours, l.hours) AS h FROM hc.overtime_line l
                    JOIN hc.overtime_request r ON r.id = l.request_id
                    WHERE l.employee_id = ? AND r.status IN ('APPROVED','POSTED','DONE') AND r.work_date BETWEEN ? AND ?""",
                    (rs, i) -> new PayrollCalculator.Overtime(rs.getString(1), rs.getBigDecimal(2)), empId, Date.valueOf(start), Date.valueOf(end));
            List<Map<String, Object>> loans = jdbc.queryForList("""
                    SELECT id, LEAST(installment_amount, principal - repaid_amount) AS due FROM hc.employee_loan
                    WHERE employee_id = ? AND status = 'POSTED' AND start_period <= ? AND repaid_amount < principal""",
                    empId, run.getPeriod());
            BigDecimal loanDue = loans.stream().map(l -> (BigDecimal) l.get("due")).reduce(BigDecimal.ZERO, BigDecimal::add);
            PayrollCalculator.Ytd ytd = december ? ytd(empId, ym) : PayrollCalculator.Ytd.ZERO;

            PayrollCalculator.Result r;
            String category = (String) pt[1];
            try {
                r = PayrollCalculator.calculate(new PayrollCalculator.Input(salary, workDays.size(), payable, absent, ot, loanDue,
                        category, (BigDecimal) pt[0], december, ytd), p, defs, ter.get(category));
            } catch (PayrollCalculator.MissingTerTable ex) {
                warnings.add("ERROR: " + label + " — " + ex.getMessage());
                continue;
            }
            if (r.net().signum() < 0) {
                warnings.add("ERROR: " + label + " — gaji bersih negatif, periksa potongan");
            }
            Long slipId = jdbc.queryForObject("""
                            INSERT INTO hc.payroll_slip (run_id, employee_id, cost_center_id, ptkp_status, ter_category, ter_rate,
                                work_days, present_days, absent_days, overtime_hours, gross, taxable_gross, total_deduction, net,
                                employer_cost, pph21)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class,
                    run.getId(), empId, e.get("cost_center_id"), e.get("ptkp_status"), category, r.terRate(), workDays.size(),
                    present, absent, r.overtimeHours(), r.gross(), r.taxableGross(), r.deductions(), r.net(), r.employerCost(), r.pph21());
            for (PayrollCalculator.Line l : r.lines()) {
                jdbc.update("""
                        INSERT INTO hc.payroll_line (slip_id, component_code, component_name, kind, account_code, quantity, amount, seq)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", slipId, l.code(), l.name(), l.kind(), l.account(), l.quantity(), l.amount(), l.seq());
            }
            for (Map<String, Object> l : loans) {
                BigDecimal due = (BigDecimal) l.get("due");
                if (due.signum() > 0) {
                    jdbc.update("INSERT INTO hc.loan_repayment (loan_id, slip_id, amount) VALUES (?, ?, ?)", l.get("id"), slipId, due);
                }
            }
            if (e.get("cost_center_id") == null) {
                warnings.add("ERROR: " + label + " — cost center karyawan belum diisi (dasar jurnal beban)");
            }
            count++;
            tGross = tGross.add(r.gross());
            tDed = tDed.add(r.deductions());
            tNet = tNet.add(r.net());
            tEmp = tEmp.add(r.employerCost());
            tPph = tPph.add(r.pph21());
        }
        if (count == 0) {
            warnings.add("ERROR: tidak ada karyawan dengan data gaji pokok di plant ini");
        }
        run.setEmployeeCount(count);
        run.setTotalGross(tGross);
        run.setTotalDeduction(tDed);
        run.setTotalNet(tNet);
        run.setTotalEmployer(tEmp);
        run.setTotalPph21(tPph);
        run.setCalculatedAt(time.now());
        run.setWarnings(warnings.isEmpty() ? null : String.join("\n", warnings));
    }

    private PayrollCalculator.Ytd ytd(Long empId, YearMonth ym) {
        Map<String, Object> r = jdbc.queryForMap("""
                SELECT COALESCE(SUM(s.taxable_gross), 0) AS g, COALESCE(SUM(s.pph21), 0) AS t,
                       COALESCE(SUM((SELECT COALESCE(SUM(l.amount), 0) FROM hc.payroll_line l
                                     WHERE l.slip_id = s.id AND l.component_code IN ('JHT_E','JP_E'))), 0) AS p
                FROM hc.payroll_slip s JOIN hc.payroll_run r ON r.id = s.run_id
                WHERE s.employee_id = ? AND r.status IN ('POSTED','DONE') AND r.period LIKE ? AND r.period < ?""",
                empId, ym.getYear() + "%", ym.format(YYYYMM));
        return new PayrollCalculator.Ytd((BigDecimal) r.get("g"), (BigDecimal) r.get("t"), (BigDecimal) r.get("p"));
    }

    /** Posting: jurnal beban gaji per cost center & hutang (gaji, PPh 21, BPJS), cicilan pinjaman tercatat. */
    @Transactional
    public void post(PayrollRun run) {
        Map<String, BigDecimal[]> acc = new LinkedHashMap<>();
        jdbc.query("""
                SELECT s.cost_center_id, l.kind, l.account_code, SUM(l.amount) AS amt
                FROM hc.payroll_line l JOIN hc.payroll_slip s ON s.id = l.slip_id
                WHERE s.run_id = ? GROUP BY s.cost_center_id, l.kind, l.account_code""", rs -> {
            Long cc = (Long) rs.getObject("cost_center_id");
            String kind = rs.getString("kind");
            String account = rs.getString("account_code");
            BigDecimal amt = rs.getBigDecimal("amt");
            switch (kind) {
                case "EARNING" -> add(acc, account, cc, amt);
                case "EMPLOYER" -> {
                    add(acc, account, cc, amt);
                    add(acc, "2107", null, amt.negate());
                }
                default -> add(acc, account, null, amt.negate());
            }
        }, run.getId());
        add(acc, "2103", null, run.getTotalNet().negate());
        List<JournalPostingService.IdLine> lines = new ArrayList<>();
        String desc = "Payroll " + run.getPeriod() + " · " + run.getDocNo();
        acc.forEach((k, v) -> {
            String[] parts = k.split("\\|");
            Long cc = "-".equals(parts[1]) ? null : Long.valueOf(parts[1]);
            BigDecimal amt = v[0];
            lines.add(new JournalPostingService.IdLine(journals.accountId(parts[0]), cc, desc,
                    amt.signum() > 0 ? amt : null, amt.signum() < 0 ? amt.negate() : null));
        });
        journals.postIds(run.getPlantId(), run.getDocDate(), lines, "PAYR", run.getId(), run.getDocNo(), desc);
        jdbc.update("""
                UPDATE hc.employee_loan l SET repaid_amount = repaid_amount + x.amt, updated_at = now()
                FROM (SELECT r.loan_id, SUM(r.amount) AS amt FROM hc.loan_repayment r JOIN hc.payroll_slip s ON s.id = r.slip_id
                      WHERE s.run_id = ? GROUP BY r.loan_id) x
                WHERE l.id = x.loan_id""", run.getId());
    }

    /** Reversal payroll: jurnal dibalik dan cicilan pinjaman dikembalikan. */
    @Transactional
    public void reverse(PayrollRun run, LocalDate date, String reason) {
        journals.reverseFor("PAYR", run.getId(), date, reason);
        jdbc.update("""
                UPDATE hc.employee_loan l SET repaid_amount = repaid_amount - x.amt, updated_at = now()
                FROM (SELECT r.loan_id, SUM(r.amount) AS amt FROM hc.loan_repayment r JOIN hc.payroll_slip s ON s.id = r.slip_id
                      WHERE s.run_id = ? GROUP BY r.loan_id) x
                WHERE l.id = x.loan_id""", run.getId());
    }

    private static void add(Map<String, BigDecimal[]> acc, String account, Long cc, BigDecimal amount) {
        BigDecimal[] cell = acc.computeIfAbsent(account + "|" + (cc == null ? "-" : cc), k -> new BigDecimal[]{BigDecimal.ZERO});
        cell[0] = cell[0].add(amount);
    }

    // ------------------------------------------------------------------ slip

    public record SlipRow(Long id, Long runId, String runDocNo, String period, String runStatus, Long employeeId, String nik,
                          String name, String costCenter, String ptkpStatus, String terCategory, BigDecimal terRate,
                          int workDays, int presentDays, int absentDays, BigDecimal overtimeHours, BigDecimal gross,
                          BigDecimal taxableGross, BigDecimal totalDeduction, BigDecimal net, BigDecimal employerCost,
                          BigDecimal pph21, String bankName, String bankAccountNo) {
    }

    public record SlipLine(String code, String name, String kind, BigDecimal quantity, BigDecimal amount) {
    }

    public record Slip(SlipRow slip, List<SlipLine> lines) {
    }

    private static final String SLIP_SQL = """
            SELECT s.*, r.doc_no AS run_no, r.period, r.status AS run_status, e.nik, e.name, e.bank_name, e.bank_account_no,
                   cc.code AS cc_code
            FROM hc.payroll_slip s JOIN hc.payroll_run r ON r.id = s.run_id JOIN hc.employee e ON e.id = s.employee_id
            LEFT JOIN sys.cost_center cc ON cc.id = s.cost_center_id""";

    List<SlipRow> slips(Long runId) {
        return jdbc.query(SLIP_SQL + " WHERE s.run_id = ? ORDER BY e.nik", (rs, i) -> slipRow(rs), runId);
    }

    List<SlipRow> mySlips(Long employeeId) {
        return jdbc.query(SLIP_SQL + " WHERE s.employee_id = ? AND r.status IN ('POSTED','DONE') ORDER BY r.period DESC",
                (rs, i) -> slipRow(rs), employeeId);
    }

    Slip slip(Long slipId) {
        SlipRow row = jdbc.query(SLIP_SQL + " WHERE s.id = ?", (rs, i) -> slipRow(rs), slipId).stream().findFirst()
                .orElseThrow(() -> new BusinessException("SLIP", "Slip tidak ditemukan"));
        List<SlipLine> lines = jdbc.query("SELECT component_code, component_name, kind, quantity, amount FROM hc.payroll_line WHERE slip_id = ? ORDER BY seq",
                (rs, i) -> new SlipLine(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5)), slipId);
        return new Slip(row, lines);
    }

    private static SlipRow slipRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SlipRow(rs.getLong("id"), rs.getLong("run_id"), rs.getString("run_no"), rs.getString("period"),
                rs.getString("run_status"), rs.getLong("employee_id"), rs.getString("nik"), rs.getString("name"),
                rs.getString("cc_code"), rs.getString("ptkp_status"), rs.getString("ter_category"), rs.getBigDecimal("ter_rate"),
                rs.getInt("work_days"), rs.getInt("present_days"), rs.getInt("absent_days"), rs.getBigDecimal("overtime_hours"),
                rs.getBigDecimal("gross"), rs.getBigDecimal("taxable_gross"), rs.getBigDecimal("total_deduction"),
                rs.getBigDecimal("net"), rs.getBigDecimal("employer_cost"), rs.getBigDecimal("pph21"),
                rs.getString("bank_name"), rs.getString("bank_account_no"));
    }

    boolean isPosted(PayrollRun run) {
        return run.getStatus() == DocStatus.POSTED || run.getStatus() == DocStatus.DONE;
    }

    private static BigDecimal num(Map<String, String> params, String key) {
        String v = params.get(key);
        if (v == null) {
            throw new BusinessException("PAYROLL_PARAM", "Parameter payroll " + key + " belum diatur (HC-10)");
        }
        return new BigDecimal(v);
    }
}
