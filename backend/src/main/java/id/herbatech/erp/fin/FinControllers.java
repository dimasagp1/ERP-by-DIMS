package id.herbatech.erp.fin;

import id.herbatech.erp.shared.document.RelatedDocumentProvider;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.meta.DashboardProvider;
import id.herbatech.erp.shared.period.PeriodLockService;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.web.LookupSource;
import id.herbatech.erp.shared.web.MasterController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** FIN-02 master akun, FIN-04 buku besar, FIN-70 kunci periode, dashboard, lookup, dokumen terkait. */
final class FinControllers {

    private FinControllers() {
    }

    // ---------------------------------------------------------------- FIN-02 Bagan akun & mapping

    @RestController
    @RequestMapping("/api/fin/accounts")
    static class AccountController extends MasterController<Account> {

        private final JdbcTemplate jdbc;

        AccountController(AccountRepository r, PermissionService p, JdbcTemplate jdbc) {
            super(r, p, Account.class);
            this.jdbc = jdbc;
        }

        @Override protected String menuCode() { return "FIN-02"; }
        @Override protected String[] immutableFields() { return new String[]{"code"}; }

        @Override
        protected void beforeSave(Account a, Account existing) {
            if (a.getParentId() != null) {
                if (existing != null && Objects.equals(a.getParentId(), existing.getId())) {
                    throw new BusinessException("ACCOUNT", "Akun tidak boleh menjadi induk dirinya sendiri");
                }
                Boolean parentPostable = jdbc.queryForObject("SELECT postable FROM fin.account WHERE id = ?", Boolean.class, a.getParentId());
                if (Boolean.TRUE.equals(parentPostable)) {
                    throw new BusinessException("ACCOUNT", "Akun induk harus akun header (tidak bisa diposting)");
                }
            }
            if (existing != null && existing.isPostable() && !a.isPostable()) {
                Long used = jdbc.queryForObject("SELECT count(*) FROM fin.journal_line WHERE account_id = ?", Long.class, existing.getId());
                if (used != null && used > 0) {
                    throw new BusinessException("ACCOUNT", "Akun sudah dipakai jurnal sehingga tidak bisa dijadikan header");
                }
            }
        }
    }

    @RestController
    @RequestMapping("/api/fin/account-mappings")
    static class AccountMappingController extends MasterController<AccountMapping> {
        AccountMappingController(AccountMappingRepository r, PermissionService p) {
            super(r, p, AccountMapping.class);
        }

        @Override protected String menuCode() { return "FIN-02"; }
        @Override protected List<String> searchFields() { return List.of("txnType", "name"); }
        @Override protected org.springframework.data.domain.Sort defaultSort() { return org.springframework.data.domain.Sort.by("txnType"); }
        @Override protected String[] immutableFields() { return new String[]{"txnType"}; }
    }

    // ---------------------------------------------------------------- FIN-04 Buku besar & neraca saldo

    @RestController
    @RequestMapping("/api/fin/ledger")
    static class LedgerController {

        record TrialBalanceRow(Long accountId, String code, String name, String type, String normalBalance,
                               BigDecimal opening, BigDecimal debit, BigDecimal credit, BigDecimal closing) {
        }

        record TrialBalance(LocalDate from, LocalDate to, List<TrialBalanceRow> rows, BigDecimal totalDebit,
                            BigDecimal totalCredit) {
        }

        record LedgerLine(Long entryId, String docNo, LocalDate docDate, String description, String sourceDocNo,
                          String costCenter, BigDecimal debit, BigDecimal credit, BigDecimal balance) {
        }

        record AccountLedger(Long accountId, String code, String name, BigDecimal opening, List<LedgerLine> lines,
                             BigDecimal closing) {
        }

        private final JdbcTemplate jdbc;
        private final PermissionService perm;

        LedgerController(JdbcTemplate jdbc, PermissionService perm) {
            this.jdbc = jdbc;
            this.perm = perm;
        }

        /** Neraca saldo: saldo awal, mutasi debit/kredit, saldo akhir per akun (hanya jurnal terposting). */
        @GetMapping("/trial-balance")
        public TrialBalance trialBalance(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
            perm.require("FIN-04", Action.VIEW);
            Long plant = UserContext.plantId();
            List<TrialBalanceRow> rows = jdbc.query("""
                            SELECT a.id, a.code, a.name, a.type, a.normal_balance,
                                   COALESCE(SUM(CASE WHEN e.doc_date < ? THEN l.debit - l.credit END), 0) AS opening,
                                   COALESCE(SUM(CASE WHEN e.doc_date BETWEEN ? AND ? THEN l.debit END), 0) AS debit,
                                   COALESCE(SUM(CASE WHEN e.doc_date BETWEEN ? AND ? THEN l.credit END), 0) AS credit
                            FROM fin.account a
                            JOIN fin.journal_line l ON l.account_id = a.id
                            JOIN fin.journal_entry e ON e.id = l.entry_id AND e.posted_at IS NOT NULL AND e.plant_id = ?
                            WHERE e.doc_date <= ?
                            GROUP BY a.id, a.code, a.name, a.type, a.normal_balance
                            ORDER BY a.code""",
                    (rs, i) -> {
                        BigDecimal opening = rs.getBigDecimal("opening");
                        BigDecimal debit = rs.getBigDecimal("debit");
                        BigDecimal credit = rs.getBigDecimal("credit");
                        return new TrialBalanceRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                                rs.getString("type"), rs.getString("normal_balance"), opening, debit, credit,
                                opening.add(debit).subtract(credit));
                    },
                    Date.valueOf(from), Date.valueOf(from), Date.valueOf(to), Date.valueOf(from), Date.valueOf(to), plant, Date.valueOf(to));
            BigDecimal td = rows.stream().map(TrialBalanceRow::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal tc = rows.stream().map(TrialBalanceRow::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new TrialBalance(from, to, rows, td, tc);
        }

        /** Buku besar satu akun dengan saldo berjalan; tiap baris bisa ditelusuri ke dokumen sumber. */
        @GetMapping("/accounts/{accountId}")
        public AccountLedger account(@PathVariable Long accountId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
            perm.require("FIN-04", Action.VIEW);
            Long plant = UserContext.plantId();
            Map<String, Object> acc = jdbc.queryForMap("SELECT code, name FROM fin.account WHERE id = ?", accountId);
            BigDecimal opening = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(l.debit - l.credit), 0) FROM fin.journal_line l
                    JOIN fin.journal_entry e ON e.id = l.entry_id
                    WHERE l.account_id = ? AND e.posted_at IS NOT NULL AND e.plant_id = ? AND e.doc_date < ?""",
                    BigDecimal.class, accountId, plant, Date.valueOf(from));
            List<LedgerLine> lines = new ArrayList<>();
            BigDecimal[] running = {opening};
            jdbc.query("""
                            SELECT e.id, e.doc_no, e.doc_date, COALESCE(l.description, e.description) AS description,
                                   e.source_doc_no, cc.code AS cc, l.debit, l.credit
                            FROM fin.journal_line l
                            JOIN fin.journal_entry e ON e.id = l.entry_id
                            LEFT JOIN sys.cost_center cc ON cc.id = l.cost_center_id
                            WHERE l.account_id = ? AND e.posted_at IS NOT NULL AND e.plant_id = ? AND e.doc_date BETWEEN ? AND ?
                            ORDER BY e.doc_date, e.id, l.line_no""",
                    rs -> {
                        running[0] = running[0].add(rs.getBigDecimal("debit")).subtract(rs.getBigDecimal("credit"));
                        lines.add(new LedgerLine(rs.getLong("id"), rs.getString("doc_no"), rs.getDate("doc_date").toLocalDate(),
                                rs.getString("description"), rs.getString("source_doc_no"), rs.getString("cc"),
                                rs.getBigDecimal("debit"), rs.getBigDecimal("credit"), running[0]));
                    }, accountId, plant, Date.valueOf(from), Date.valueOf(to));
            return new AccountLedger(accountId, (String) acc.get("code"), (String) acc.get("name"), opening, lines, running[0]);
        }
    }

    // ---------------------------------------------------------------- FIN-70 Kunci periode

    @RestController
    @RequestMapping("/api/fin/periods")
    static class PeriodController {

        record LockRequest(@NotBlank String module, @Min(2000) @Max(2100) int year, @Min(1) @Max(12) int month, boolean locked) {
        }

        private final PeriodLockService periods;
        private final PermissionService perm;

        PeriodController(PeriodLockService periods, PermissionService perm) {
            this.periods = periods;
            this.perm = perm;
        }

        @GetMapping
        public Map<String, Object> list(@RequestParam int year) {
            perm.require("FIN-70", Action.VIEW);
            return Map.of("modules", PeriodLockService.MODULES, "locks", periods.list(year),
                    "canLock", perm.has("FIN-70", Action.POST));
        }

        /** Kunci/buka periode per modul. Membuka kembali periode hanya oleh Manager FIN (PRD §16 poin 4). */
        @PostMapping("/lock")
        public Map<String, Object> lock(@Valid @RequestBody LockRequest req) {
            perm.require("FIN-70", Action.POST);
            periods.setLocked(req.module(), req.year(), req.month(), req.locked(), UserContext.userId());
            return list(req.year());
        }
    }

    // ---------------------------------------------------------------- Dashboard, lookup, dokumen terkait

    @Component
    static class FinDashboard implements DashboardProvider {

        private final JdbcTemplate jdbc;
        private final PeriodLockService periods;
        private final TimeService time;
        private final BudgetService budgets;

        FinDashboard(JdbcTemplate jdbc, PeriodLockService periods, TimeService time, BudgetService budgets) {
            this.jdbc = jdbc;
            this.periods = periods;
            this.time = time;
            this.budgets = budgets;
        }

        @Override
        public String appCode() {
            return "FIN";
        }

        @Override
        public List<Kpi> kpis(Long plantId) {
            LocalDate today = time.today();
            BigDecimal cash = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(l.debit - l.credit), 0) FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id
                    WHERE e.posted_at IS NOT NULL AND e.plant_id = ? AND l.account_id IN (SELECT gl_account_id FROM fin.bank_account WHERE active)""",
                    BigDecimal.class, plantId);
            BigDecimal apDue = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(payable - advance_applied - paid_amount), 0) FROM fin.ap_invoice
                    WHERE status = 'POSTED' AND plant_id = ? AND due_date <= ?""", BigDecimal.class, plantId, Date.valueOf(today.plusDays(7)));
            BigDecimal arOld = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(total - received_amount), 0) FROM fin.ar_invoice
                    WHERE status = 'POSTED' AND plant_id = ? AND due_date < ?""", BigDecimal.class, plantId, Date.valueOf(today.minusDays(60)));
            BigDecimal budget = BigDecimal.ZERO, actual = BigDecimal.ZERO;
            for (Map<String, Object> r : budgets.realization(plantId, today.getYear(), today.getMonthValue())) {
                budget = budget.add((BigDecimal) r.get("budget"));
                actual = actual.add((BigDecimal) r.get("actual"));
            }
            boolean locked = periods.isLocked("FIN", today);
            return List.of(
                    new Kpi("Kas & bank", rupiah(cash), "Saldo buku semua rekening", "FIN-31"),
                    new Kpi("Hutang jatuh tempo 7 hari", rupiah(apDue), "Termasuk yang sudah lewat tempo", "FIN-12"),
                    new Kpi("Piutang > 60 hari", rupiah(arOld), "Memicu penahanan faktur baru", "FIN-22"),
                    new Kpi("Realisasi anggaran YTD", budget.signum() == 0 ? "–"
                            : actual.multiply(BigDecimal.valueOf(100)).divide(budget, 0, java.math.RoundingMode.HALF_UP) + "%",
                            budget.signum() == 0 ? "Belum ada anggaran berlaku" : rupiah(actual) + " dari " + rupiah(budget), "FIN-51"),
                    new Kpi("Periode " + "%02d/%d".formatted(today.getMonthValue(), today.getYear()),
                            locked ? "Terkunci" : "Terbuka", "FIN-70 kunci periode", "FIN-70"));
        }

        static String rupiah(BigDecimal v) {
            BigDecimal abs = v.abs();
            String sign = v.signum() < 0 ? "−" : "";
            if (abs.compareTo(new BigDecimal("1000000000")) >= 0) {
                return sign + "Rp " + abs.divide(new BigDecimal("1000000000"), 2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " M";
            }
            if (abs.compareTo(new BigDecimal("1000000")) >= 0) {
                return sign + "Rp " + abs.divide(new BigDecimal("1000000"), 1, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " jt";
            }
            return sign + "Rp " + String.format("%,d", abs.longValue()).replace(',', '.');
        }
    }

    @Component
    static class FinLookups implements LookupSource {
        @Override
        public Map<String, Def> lookups() {
            return Map.of(
                    "accounts", new Def("fin.account", "code", "name", "type", "active AND postable", Set.of("type", "requires_cost_center")),
                    "account-headers", new Def("fin.account", "code", "name", "type", "active AND NOT postable", Set.of("type")),
                    "bank-accounts", new Def("fin.bank_account", "code", "name", "kind", "active", Set.of("kind", "plant_id")),
                    "asset-categories", Def.of("fin.asset_category", "code", "name"),
                    // Dokumen terbuka untuk alokasi pembayaran/penerimaan & pemotongan uang muka (extra = sisa).
                    "ap-open", new Def("""
                            (SELECT id, doc_no, supplier_invoice_no, partner_id, status,
                                    (payable - advance_applied - paid_amount)::text AS sisa,
                                    payable - advance_applied - paid_amount AS sisa_num FROM fin.ap_invoice) x""",
                            "doc_no", "supplier_invoice_no", "sisa", "status = 'POSTED' AND sisa_num > 0", Set.of("partner_id")),
                    "ar-open", new Def("""
                            (SELECT id, doc_no, COALESCE(customer_po, description, '') AS ref, partner_id, status,
                                    (total - received_amount)::text AS sisa, total - received_amount AS sisa_num FROM fin.ar_invoice) x""",
                            "doc_no", "ref", "sisa", "status = 'POSTED' AND sisa_num > 0", Set.of("partner_id")),
                    "ap-advances", new Def("""
                            (SELECT id, doc_no, COALESCE(reference, '') AS ref, partner_id, status, kind,
                                    (amount - advance_used)::text AS sisa, amount - advance_used AS sisa_num FROM fin.payment) x""",
                            "doc_no", "ref", "sisa", "kind = 'ADVANCE' AND status = 'POSTED' AND sisa_num > 0", Set.of("partner_id")),
                    "kk-advances", new Def("""
                            (SELECT id, doc_no, description, employee_id, status, kind, settled_amount, amount::text AS nilai
                             FROM fin.cash_voucher) x""",
                            "doc_no", "description", "nilai", "kind = 'ADVANCE' AND status = 'POSTED' AND settled_amount = 0", Set.of("employee_id")));
        }
    }

    @Component
    static class FinRelatedDocuments implements RelatedDocumentProvider {

        private final JdbcTemplate jdbc;

        FinRelatedDocuments(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public List<RelatedDoc> related(String docType, Long docId) {
            if ("JV".equals(docType)) {
                return jdbc.query("""
                                SELECT 'JV' AS t, r.id, r.doc_no, r.status, 'Reversal' AS rel FROM fin.journal_entry r WHERE r.reversal_of_id = ?
                                UNION ALL
                                SELECT 'JV', o.id, o.doc_no, o.status, 'Jurnal asal' FROM fin.journal_entry j
                                JOIN fin.journal_entry o ON o.id = j.reversal_of_id WHERE j.id = ?""",
                        (rs, i) -> new RelatedDoc(rs.getString(1), rs.getLong(2), rs.getString(3), "FIN-03", rs.getString(4), rs.getString(5)),
                        docId, docId);
            }
            return jdbc.query("SELECT id, doc_no, status FROM fin.journal_entry WHERE source_doc_type = ? AND source_doc_id = ? ORDER BY id",
                    (rs, i) -> new RelatedDoc("JV", rs.getLong(1), rs.getString(2), "FIN-03", rs.getString(3), "Jurnal otomatis"),
                    docType, docId);
        }
    }
}
