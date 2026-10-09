package id.herbatech.erp.fin;

import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.integration.IntegrationService;
import id.herbatech.erp.shared.meta.KpiProvider;
import id.herbatech.erp.shared.period.BusinessCalendar;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import id.herbatech.erp.shared.web.MasterController;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Laporan & alat bantu FIN: kontrol anggaran, umur piutang/hutang, rekonsiliasi bank, proyeksi kas, laporan keuangan, BSC. */
final class FinReports {

    private FinReports() {
    }

    // ---------------------------------------------------------------- master FIN-99

    @RestController
    @RequestMapping("/api/fin/bank-accounts")
    static class BankAccountController extends MasterController<BankAccount> {
        BankAccountController(BankAccountRepository r, PermissionService p) { super(r, p, BankAccount.class); }
        @Override protected String menuCode() { return "FIN-31"; }
        @Override protected String[] immutableFields() { return new String[]{"code"}; }
    }

    @RestController
    @RequestMapping("/api/fin/params")
    static class FinParamController extends MasterController<FinParam> {
        FinParamController(FinParamRepository r, PermissionService p) { super(r, p, FinParam.class); }
        @Override protected String menuCode() { return "FIN-02"; }
        @Override protected List<String> searchFields() { return List.of("key", "description"); }
        @Override protected Sort defaultSort() { return Sort.by("key"); }
        @Override protected String[] immutableFields() { return new String[]{"key"}; }
    }

    // ---------------------------------------------------------------- FIN-51, FIN-22, umur hutang

    @RestController
    @RequestMapping("/api/fin")
    static class AnalysisController {

        private final JdbcTemplate jdbc;
        private final PermissionService perm;
        private final BudgetService budgets;
        private final TimeService time;

        AnalysisController(JdbcTemplate jdbc, PermissionService perm, BudgetService budgets, TimeService time) {
            this.jdbc = jdbc;
            this.perm = perm;
            this.budgets = budgets;
            this.time = time;
        }

        /** FIN-51: anggaran vs realisasi vs komitmen per cost center × akun, kumulatif s.d. bulan. */
        @GetMapping("/budget-control")
        public List<Map<String, Object>> budgetControl(@RequestParam int year, @RequestParam int month) {
            perm.require("FIN-51", Action.VIEW);
            return budgets.realization(UserContext.plantId(), year, month);
        }

        /** FIN-22 umur piutang per customer: belum jatuh tempo, 1–30, 31–60, 61–90, > 90 hari. */
        @GetMapping("/ar-aging")
        public List<Map<String, Object>> arAging(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
            perm.require("FIN-22", Action.VIEW);
            return aging("fin.ar_invoice", "total - received_amount", asOf, true);
        }

        /** Umur hutang per supplier (FIN-12 jadwal pembayaran). */
        @GetMapping("/ap-aging")
        public List<Map<String, Object>> apAging(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
            perm.require("FIN-12", Action.VIEW);
            return aging("fin.ap_invoice", "payable - advance_applied - paid_amount", asOf, false);
        }

        private List<Map<String, Object>> aging(String table, String outstanding, LocalDate asOf, boolean withLimit) {
            Date d = Date.valueOf(asOf == null ? time.today() : asOf);
            return jdbc.queryForList("""
                    SELECT p.id AS partner_id, p.code, p.name, %s
                           SUM(x.o) AS total,
                           SUM(x.o) FILTER (WHERE x.due_date >= ?) AS current,
                           SUM(x.o) FILTER (WHERE x.due_date < ? AND x.due_date >= ?::date - 30) AS d1_30,
                           SUM(x.o) FILTER (WHERE x.due_date < ?::date - 30 AND x.due_date >= ?::date - 60) AS d31_60,
                           SUM(x.o) FILTER (WHERE x.due_date < ?::date - 60 AND x.due_date >= ?::date - 90) AS d61_90,
                           SUM(x.o) FILTER (WHERE x.due_date < ?::date - 90) AS d90,
                           count(*) AS invoices
                    FROM (SELECT partner_id, due_date, %s AS o FROM %s WHERE status = 'POSTED' AND plant_id = ? AND doc_date <= ?) x
                    JOIN sys.partner p ON p.id = x.partner_id
                    WHERE x.o > 0 GROUP BY p.id, p.code, p.name%s ORDER BY total DESC"""
                    .formatted(withLimit ? "p.credit_limit," : "", outstanding, table, withLimit ? ", p.credit_limit" : ""),
                    d, d, d, d, d, d, d, d, UserContext.plantId(), d);
        }

        /** FIN-32 proyeksi arus kas 13 minggu: piutang & hutang menurut jatuh tempo, payroll sesuai bulan lalu. */
        @GetMapping("/cash-forecast")
        public Map<String, Object> forecast(@RequestParam(defaultValue = "13") int weeks) {
            perm.require("FIN-32", Action.VIEW);
            Long plant = UserContext.plantId();
            LocalDate start = time.today();
            BigDecimal opening = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(l.debit - l.credit), 0) FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id
                    WHERE e.posted_at IS NOT NULL AND e.plant_id = ? AND l.account_id IN (SELECT gl_account_id FROM fin.bank_account WHERE active)""",
                    BigDecimal.class, plant);
            BigDecimal payroll = jdbc.queryForList("""
                    SELECT total_net + total_pph21 FROM hc.payroll_run WHERE plant_id = ? AND status IN ('POSTED','DONE')
                    ORDER BY period DESC LIMIT 1""", BigDecimal.class, plant).stream().findFirst().orElse(BigDecimal.ZERO);
            List<Map<String, Object>> rows = new ArrayList<>();
            BigDecimal balance = opening;
            for (int w = 0; w < Math.min(weeks, 26); w++) {
                LocalDate from = start.plusWeeks(w);
                LocalDate to = from.plusDays(6);
                boolean first = w == 0;
                BigDecimal in = jdbc.queryForObject("""
                        SELECT COALESCE(SUM(total - received_amount), 0) FROM fin.ar_invoice
                        WHERE status = 'POSTED' AND plant_id = ? AND total > received_amount AND due_date <= ? AND (? OR due_date >= ?)""",
                        BigDecimal.class, plant, Date.valueOf(to), first, Date.valueOf(from));
                BigDecimal out = jdbc.queryForObject("""
                        SELECT COALESCE(SUM(payable - advance_applied - paid_amount), 0) FROM fin.ap_invoice
                        WHERE status = 'POSTED' AND plant_id = ? AND payable > advance_applied + paid_amount AND due_date <= ? AND (? OR due_date >= ?)""",
                        BigDecimal.class, plant, Date.valueOf(to), first, Date.valueOf(from));
                BigDecimal pay = BigDecimal.ZERO;
                for (LocalDate dd = from; !dd.isAfter(to); dd = dd.plusDays(1)) {
                    if (dd.getDayOfMonth() == 25) {
                        pay = payroll;
                    }
                }
                balance = balance.add(in).subtract(out).subtract(pay);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("week", w + 1);
                r.put("from", from);
                r.put("to", to);
                r.put("inflow", in);
                r.put("outflowAp", out);
                r.put("outflowPayroll", pay);
                r.put("closing", balance);
                rows.add(r);
            }
            return Map.of("opening", opening, "weeks", rows, "note",
                    "Masuk = piutang terposting menurut jatuh tempo (yang sudah lewat masuk minggu pertama); keluar = hutang menurut jatuh tempo + payroll terakhir pada tanggal 25.");
        }
    }

    // ---------------------------------------------------------------- FIN-31 Rekonsiliasi bank

    @RestController
    @RequestMapping("/api/fin/bank-statements")
    static class BankReconciliationController {

        record Match(String docType, Long docId) {
        }

        private final JdbcTemplate jdbc;
        private final PermissionService perm;
        private final FinSupport fin;

        BankReconciliationController(JdbcTemplate jdbc, PermissionService perm, FinSupport fin) {
            this.jdbc = jdbc;
            this.perm = perm;
            this.fin = fin;
        }

        /** Impor mutasi CSV: {@code tanggal;keterangan;referensi;masuk;keluar}. */
        @PostMapping("/import")
        @Transactional
        public Map<String, Object> importCsv(@RequestParam Long bankAccountId, @RequestPart("file") MultipartFile file) throws IOException {
            perm.require("FIN-31", Action.CREATE);
            fin.glAccountOfBank(bankAccountId);
            String batch = "IMP-" + UUID.randomUUID().toString().substring(0, 8);
            int ok = 0;
            List<String> errors = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int no = 0;
                while ((line = r.readLine()) != null) {
                    no++;
                    line = line.replace("﻿", "").trim();
                    if (line.isEmpty() || (no == 1 && line.toLowerCase().startsWith("tanggal"))) {
                        continue;
                    }
                    String[] p = line.split(";", -1);
                    try {
                        LocalDate d = p[0].contains("/") ? LocalDate.parse(p[0].trim(), DateTimeFormatter.ofPattern("dd/MM/yyyy")) : LocalDate.parse(p[0].trim());
                        BigDecimal in = num(p.length > 3 ? p[3] : "");
                        BigDecimal out = num(p.length > 4 ? p[4] : "");
                        if (in.signum() == 0 && out.signum() == 0) {
                            throw new IllegalArgumentException("nilai kosong");
                        }
                        jdbc.update("""
                                INSERT INTO fin.bank_statement_line (bank_account_id, txn_date, description, reference, money_in, money_out, import_batch)
                                VALUES (?, ?, ?, ?, ?, ?, ?)""", bankAccountId, Date.valueOf(d), p.length > 1 ? p[1].trim() : null,
                                p.length > 2 ? p[2].trim() : null, in, out, batch);
                        ok++;
                    } catch (RuntimeException e) {
                        if (errors.size() < 30) {
                            errors.add("Baris " + no + ": " + e.getMessage());
                        }
                    }
                }
            }
            int matched = autoMatch(bankAccountId);
            return Map.of("imported", ok, "errors", errors, "autoMatched", matched, "batch", batch);
        }

        private static BigDecimal num(String s) {
            String v = s.trim().replace(".", "").replace(",", ".");
            return v.isEmpty() ? BigDecimal.ZERO : new BigDecimal(v);
        }

        @GetMapping
        public Map<String, Object> view(@RequestParam Long bankAccountId,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
            perm.require("FIN-31", Action.VIEW);
            Long gl = fin.glAccountOfBank(bankAccountId);
            List<Map<String, Object>> lines = jdbc.queryForList("""
                    SELECT * FROM fin.bank_statement_line WHERE bank_account_id = ? AND txn_date BETWEEN ? AND ? ORDER BY txn_date, id""",
                    bankAccountId, Date.valueOf(from), Date.valueOf(to));
            List<Map<String, Object>> book = unmatchedBook(bankAccountId, to);
            BigDecimal glBalance = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(l.debit - l.credit), 0) FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id
                    WHERE e.posted_at IS NOT NULL AND l.account_id = ? AND e.doc_date <= ?""", BigDecimal.class, gl, Date.valueOf(to));
            BigDecimal stmt = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(money_in - money_out), 0) FROM fin.bank_statement_line WHERE bank_account_id = ? AND txn_date <= ?""",
                    BigDecimal.class, bankAccountId, Date.valueOf(to));
            return Map.of("lines", lines, "unmatchedBook", book, "glBalance", glBalance, "statementBalance", stmt);
        }

        private List<Map<String, Object>> unmatchedBook(Long bankAccountId, LocalDate to) {
            return jdbc.queryForList("""
                    SELECT 'PAY' AS doc_type, id AS doc_id, doc_no, doc_date, 0 AS money_in, amount AS money_out, reference
                    FROM fin.payment WHERE bank_account_id = ? AND status = 'POSTED' AND NOT reconciled AND doc_date <= ?
                    UNION ALL
                    SELECT 'RCV', id, doc_no, doc_date, amount, 0, reference
                    FROM fin.receipt WHERE bank_account_id = ? AND status = 'POSTED' AND NOT reconciled AND doc_date <= ?
                    ORDER BY doc_date""", bankAccountId, Date.valueOf(to), bankAccountId, Date.valueOf(to));
        }

        /** Cocokkan otomatis: nilai sama persis dan tanggal dalam toleransi FIN-99 (BANK_MATCH_DAYS). */
        @PostMapping("/auto-match")
        @Transactional
        public Map<String, Object> auto(@RequestParam Long bankAccountId) {
            perm.require("FIN-31", Action.EDIT);
            return Map.of("matched", autoMatch(bankAccountId));
        }

        int autoMatch(Long bankAccountId) {
            int tol = Integer.parseInt(fin.param("BANK_MATCH_DAYS", "3"));
            int n = 0;
            for (Map<String, Object> l : jdbc.queryForList("""
                    SELECT id, txn_date, money_in, money_out FROM fin.bank_statement_line
                    WHERE bank_account_id = ? AND matched_doc_id IS NULL ORDER BY txn_date""", bankAccountId)) {
                BigDecimal in = (BigDecimal) l.get("money_in");
                BigDecimal out = (BigDecimal) l.get("money_out");
                Date d = (Date) l.get("txn_date");
                String table = in.signum() > 0 ? "fin.receipt" : "fin.payment";
                BigDecimal amt = in.signum() > 0 ? in : out;
                List<Map<String, Object>> cands = jdbc.queryForList("""
                        SELECT id, doc_no FROM %s WHERE bank_account_id = ? AND status = 'POSTED' AND NOT reconciled AND amount = ?
                          AND doc_date BETWEEN ?::date - ? AND ?::date + ? ORDER BY abs(doc_date - ?::date), id LIMIT 1""".formatted(table),
                        bankAccountId, amt, d, tol, d, tol, d);
                if (!cands.isEmpty()) {
                    match(((Number) l.get("id")).longValue(), in.signum() > 0 ? "RCV" : "PAY", ((Number) cands.getFirst().get("id")).longValue());
                    n++;
                }
            }
            return n;
        }

        @PostMapping("/lines/{lineId}/match")
        @Transactional
        public Map<String, Object> manual(@PathVariable Long lineId, @RequestBody Match m) {
            perm.require("FIN-31", Action.EDIT);
            match(lineId, m.docType(), m.docId());
            return Map.of("ok", true);
        }

        @PostMapping("/lines/{lineId}/unmatch")
        @Transactional
        public Map<String, Object> unmatch(@PathVariable Long lineId) {
            perm.require("FIN-31", Action.EDIT);
            Map<String, Object> l = jdbc.queryForMap("SELECT matched_doc_type, matched_doc_id FROM fin.bank_statement_line WHERE id = ?", lineId);
            if (l.get("matched_doc_id") != null) {
                String table = "RCV".equals(l.get("matched_doc_type")) ? "fin.receipt" : "fin.payment";
                jdbc.update("UPDATE " + table + " SET reconciled = FALSE WHERE id = ?", l.get("matched_doc_id"));
            }
            jdbc.update("""
                    UPDATE fin.bank_statement_line SET matched_doc_type = NULL, matched_doc_id = NULL, matched_doc_no = NULL,
                        matched_by = NULL, matched_at = NULL WHERE id = ?""", lineId);
            return Map.of("ok", true);
        }

        private void match(Long lineId, String docType, Long docId) {
            if (!"PAY".equals(docType) && !"RCV".equals(docType)) {
                throw new BusinessException("RECON", "Hanya pembayaran (PAY) atau penerimaan (RCV) yang bisa dicocokkan");
            }
            String table = "RCV".equals(docType) ? "fin.receipt" : "fin.payment";
            String docNo = jdbc.queryForList("SELECT doc_no FROM " + table + " WHERE id = ? AND status = 'POSTED' AND NOT reconciled",
                    String.class, docId).stream().findFirst()
                    .orElseThrow(() -> new BusinessException("RECON", "Dokumen tidak ditemukan atau sudah direkonsiliasi"));
            int n = jdbc.update("""
                    UPDATE fin.bank_statement_line SET matched_doc_type = ?, matched_doc_id = ?, matched_doc_no = ?, matched_by = ?, matched_at = now()
                    WHERE id = ? AND matched_doc_id IS NULL""", docType, docId, docNo, UserContext.userId(), lineId);
            if (n == 0) {
                throw new BusinessException("RECON", "Baris mutasi sudah dicocokkan");
            }
            jdbc.update("UPDATE " + table + " SET reconciled = TRUE WHERE id = ?", docId);
        }
    }

    // ---------------------------------------------------------------- FIN-71 Laporan keuangan, FIN-72 manajemen & BSC

    @RestController
    @RequestMapping("/api/fin/statements")
    static class StatementController {

        private final JdbcTemplate jdbc;
        private final PermissionService perm;

        StatementController(JdbcTemplate jdbc, PermissionService perm) {
            this.jdbc = jdbc;
            this.perm = perm;
        }

        /** Saldo per akun detail (posted, plant aktif; plantId=0 → konsolidasi semua plant). */
        private List<Map<String, Object>> balances(LocalDate from, LocalDate to, String types, boolean consolidated) {
            return jdbc.queryForList("""
                    SELECT a.id, a.code, a.name, a.type, a.normal_balance, p.code AS group_code, p.name AS group_name,
                           COALESCE(SUM(l.debit - l.credit), 0) AS dc
                    FROM fin.account a LEFT JOIN fin.account p ON p.id = a.parent_id
                    LEFT JOIN fin.journal_line l ON l.account_id = a.id
                    LEFT JOIN fin.journal_entry e ON e.id = l.entry_id
                    WHERE a.postable AND a.type = ANY (string_to_array(?, ','))
                      AND (e.id IS NULL OR (e.posted_at IS NOT NULL AND e.doc_date BETWEEN ? AND ? AND (? OR e.plant_id = ?)))
                    GROUP BY a.id, a.code, a.name, a.type, a.normal_balance, p.code, p.name
                    HAVING COALESCE(SUM(l.debit - l.credit), 0) <> 0
                    ORDER BY a.code""", types, Date.valueOf(from), Date.valueOf(to), consolidated, UserContext.plantId());
        }

        /** Laba rugi: pendapatan (kredit) − HPP − beban. */
        @GetMapping("/income")
        public Map<String, Object> income(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(defaultValue = "false") boolean consolidated) {
            perm.require("FIN-71", Action.VIEW);
            List<Map<String, Object>> rows = balances(from, to, "REVENUE,EXPENSE", consolidated);
            BigDecimal revenue = BigDecimal.ZERO, cogs = BigDecimal.ZERO, opex = BigDecimal.ZERO, other = BigDecimal.ZERO;
            for (Map<String, Object> r : rows) {
                BigDecimal amount = ((BigDecimal) r.get("dc")).negate(); // positif = menambah laba
                r.put("amount", amount);
                String code = (String) r.get("code");
                if (code.startsWith("4")) revenue = revenue.add(amount);
                else if (code.startsWith("5")) cogs = cogs.add(amount);
                else if (code.startsWith("6")) opex = opex.add(amount);
                else other = other.add(amount);
            }
            BigDecimal gross = revenue.add(cogs);
            BigDecimal operating = gross.add(opex);
            return Map.of("rows", rows, "revenue", revenue, "cogs", cogs.negate(), "grossProfit", gross, "opex", opex.negate(),
                    "operatingProfit", operating, "other", other, "netProfit", operating.add(other));
        }

        /** Neraca per tanggal: aset = liabilitas + ekuitas (+ laba berjalan). */
        @GetMapping("/balance")
        public Map<String, Object> balance(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                                           @RequestParam(defaultValue = "false") boolean consolidated) {
            perm.require("FIN-71", Action.VIEW);
            List<Map<String, Object>> rows = balances(LocalDate.of(1900, 1, 1), asOf, "ASSET,LIABILITY,EQUITY", consolidated);
            BigDecimal assets = BigDecimal.ZERO, liabilities = BigDecimal.ZERO, equity = BigDecimal.ZERO;
            for (Map<String, Object> r : rows) {
                BigDecimal dc = (BigDecimal) r.get("dc");
                String type = (String) r.get("type");
                BigDecimal amount = "ASSET".equals(type) ? dc : dc.negate();
                r.put("amount", amount);
                switch (type) {
                    case "ASSET" -> assets = assets.add(amount);
                    case "LIABILITY" -> liabilities = liabilities.add(amount);
                    default -> equity = equity.add(amount);
                }
            }
            BigDecimal profit = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(l.credit - l.debit), 0) FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id
                    JOIN fin.account a ON a.id = l.account_id
                    WHERE e.posted_at IS NOT NULL AND a.type IN ('REVENUE','EXPENSE') AND e.doc_date <= ? AND (? OR e.plant_id = ?)""",
                    BigDecimal.class, Date.valueOf(asOf), consolidated, UserContext.plantId());
            return Map.of("rows", rows, "assets", assets, "liabilities", liabilities, "equity", equity, "currentProfit", profit,
                    "balanced", assets.compareTo(liabilities.add(equity).add(profit)) == 0);
        }

        /** Arus kas (metode langsung): mutasi akun kas & bank diklasifikasikan menurut akun lawan. */
        @GetMapping("/cashflow")
        public Map<String, Object> cashflow(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
            perm.require("FIN-71", Action.VIEW);
            // Jurnal selalu seimbang: kas masuk/keluar = jumlah (kredit - debit) baris non-kas pada jurnal yang menyentuh kas.
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    WITH cash AS (SELECT gl_account_id AS id FROM fin.bank_account WHERE active),
                    entries AS (
                      SELECT DISTINCT e.id FROM fin.journal_entry e JOIN fin.journal_line l ON l.entry_id = e.id
                      WHERE e.posted_at IS NOT NULL AND e.plant_id = ? AND e.doc_date BETWEEN ? AND ? AND l.account_id IN (SELECT id FROM cash))
                    SELECT CASE WHEN a.code LIKE '15%' THEN 'INVESTASI' WHEN a.code LIKE '3%' THEN 'PENDANAAN' ELSE 'OPERASI' END AS section,
                           a.code, a.name, SUM(l.credit - l.debit) AS amount
                    FROM fin.journal_line l JOIN entries x ON x.id = l.entry_id JOIN fin.account a ON a.id = l.account_id
                    WHERE l.account_id NOT IN (SELECT id FROM cash)
                    GROUP BY 1, a.code, a.name HAVING SUM(l.credit - l.debit) <> 0 ORDER BY 1, a.code""", UserContext.plantId(), Date.valueOf(from), Date.valueOf(to));
            Map<String, BigDecimal> sections = new LinkedHashMap<>();
            for (Map<String, Object> r : rows) {
                BigDecimal amt = r.get("amount") == null ? BigDecimal.ZERO : ((BigDecimal) r.get("amount")).setScale(2, RoundingMode.HALF_UP);
                r.put("amount", amt);
                sections.merge((String) r.get("section"), amt, BigDecimal::add);
            }
            return Map.of("rows", rows, "sections", sections,
                    "net", sections.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }

    @RestController
    @RequestMapping("/api/fin/management")
    static class ManagementController {

        private final JdbcTemplate jdbc;
        private final PermissionService perm;
        private final BudgetService budgets;
        private final IntegrationService integration;

        ManagementController(JdbcTemplate jdbc, PermissionService perm, BudgetService budgets, IntegrationService integration) {
            this.jdbc = jdbc;
            this.perm = perm;
            this.budgets = budgets;
            this.integration = integration;
        }

        /** FIN-72: realisasi anggaran per departemen & pendapatan per produk (qty × harga dari faktur penjualan). */
        @GetMapping
        public Map<String, Object> report(@RequestParam int year, @RequestParam int month) {
            perm.require("FIN-72", Action.VIEW);
            Long plant = UserContext.plantId();
            Map<String, BigDecimal[]> dept = new LinkedHashMap<>();
            for (Map<String, Object> r : budgets.realization(plant, year, month)) {
                BigDecimal[] a = dept.computeIfAbsent((String) r.get("department"), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                a[0] = a[0].add((BigDecimal) r.get("budget"));
                a[1] = a[1].add((BigDecimal) r.get("actual"));
            }
            List<Map<String, Object>> departments = new ArrayList<>();
            dept.forEach((k, v) -> departments.add(Map.of("department", k, "budget", v[0], "actual", v[1],
                    "pct", v[0].signum() == 0 ? BigDecimal.ZERO : v[1].multiply(BigDecimal.valueOf(100)).divide(v[0], 1, RoundingMode.HALF_UP))));
            YearMonth ym = YearMonth.of(year, month);
            List<Map<String, Object>> products = jdbc.queryForList("""
                    SELECT COALESCE(i.code, '-') AS code, COALESCE(i.name, l.description) AS name, SUM(l.qty) AS qty, SUM(l.amount) AS revenue
                    FROM fin.ar_invoice_line l JOIN fin.ar_invoice v ON v.id = l.invoice_id LEFT JOIN sys.item i ON i.id = l.item_id
                    WHERE v.status IN ('POSTED','DONE') AND v.plant_id = ? AND v.doc_date BETWEEN ? AND ?
                    GROUP BY 1, 2 ORDER BY revenue DESC""", plant, Date.valueOf(LocalDate.of(year, 1, 1)), Date.valueOf(ym.atEndOfMonth()));
            return Map.of("departments", departments, "products", products,
                    "note", "Margin per produk tersedia setelah HPP per batch (FIN-53/54) aktif di M2.");
        }

        /** Kirim nilai keuangan ke BSC (PRD §16 poin 3). */
        @PostMapping("/send-bsc")
        public Map<String, Object> send(@RequestParam int year, @RequestParam int month) {
            perm.require("FIN-72", Action.POST);
            Map<String, Object> payload = new HashMap<>(report(year, month));
            payload.put("period", "%d%02d".formatted(year, month));
            payload.put("plant", jdbc.queryForObject("SELECT code FROM sys.plant WHERE id = ?", String.class, UserContext.plantId()));
            return Map.of("integrationLogId", integration.enqueue("BSC", "/finance", "FIN/" + payload.get("period"), payload));
        }
    }

    // ---------------------------------------------------------------- KPI FIN untuk SARMUT

    @Component
    static class FinKpis implements KpiProvider {

        private final JdbcTemplate jdbc;
        private final BudgetService budgets;
        private final BusinessCalendar calendar;

        FinKpis(JdbcTemplate jdbc, BudgetService budgets, BusinessCalendar calendar) {
            this.jdbc = jdbc;
            this.budgets = budgets;
            this.calendar = calendar;
        }

        @Override
        public Map<String, BigDecimal> compute(YearMonth period, Long plantId) {
            Map<String, BigDecimal> out = new HashMap<>();
            jdbc.query("SELECT locked_at FROM core.period_lock WHERE module_code IN ('FIN','ALL') AND locked AND year = ? AND month = ? ORDER BY locked_at LIMIT 1",
                    rs -> {
                        LocalDate lockedOn = rs.getTimestamp(1).toInstant().atZone(java.time.ZoneId.of("Asia/Jakarta")).toLocalDate();
                        out.put("FIN_CLOSING_DAYS", BigDecimal.valueOf(calendar.workingDaysBetween(period.atEndOfMonth(), lockedOn)));
                    }, period.getYear(), period.getMonthValue());
            BigDecimal budget = BigDecimal.ZERO, actual = BigDecimal.ZERO;
            for (Map<String, Object> r : budgets.realization(plantId, period.getYear(), period.getMonthValue())) {
                budget = budget.add((BigDecimal) r.get("budget"));
                actual = actual.add((BigDecimal) r.get("actual"));
            }
            if (budget.signum() > 0) {
                out.put("FIN_BUDGET", actual.multiply(BigDecimal.valueOf(100)).divide(budget, 4, RoundingMode.HALF_UP));
            }
            Date from = Date.valueOf(period.atDay(1));
            Date to = Date.valueOf(period.atEndOfMonth());
            BigDecimal sales = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(subtotal), 0) FROM fin.ar_invoice WHERE status IN ('POSTED','DONE') AND plant_id = ? AND doc_date BETWEEN ? AND ?""",
                    BigDecimal.class, plantId, from, to);
            if (sales.signum() > 0) {
                BigDecimal open = jdbc.queryForObject("""
                        SELECT COALESCE(SUM(total), 0) - COALESCE((SELECT SUM(ra.amount) FROM fin.receipt_allocation ra JOIN fin.receipt r ON r.id = ra.receipt_id
                               JOIN fin.ar_invoice i2 ON i2.id = ra.invoice_id
                               WHERE r.status IN ('POSTED','DONE') AND r.doc_date <= ? AND i2.plant_id = ?), 0)
                        FROM fin.ar_invoice WHERE status IN ('POSTED','DONE') AND plant_id = ? AND doc_date <= ?""",
                        BigDecimal.class, to, plantId, plantId, to);
                long days = ChronoUnit.DAYS.between(period.atDay(1), period.atEndOfMonth()) + 1;
                out.put("FIN_DSO", open.multiply(BigDecimal.valueOf(days)).divide(sales, 4, RoundingMode.HALF_UP));
            }
            return out;
        }
    }
}
