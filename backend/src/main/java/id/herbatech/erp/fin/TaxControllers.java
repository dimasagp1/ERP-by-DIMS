package id.herbatech.erp.fin;

import id.herbatech.erp.shared.document.NumberingService;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pajak: PPN & e-Faktur (FIN-60), PPh 21/23/4(2)/22 (FIN-61), bukti potong & SPT masa (FIN-62), rekonsiliasi fiskal (FIN-63).
 * Semua angka berasal dari dokumen terposting; format ekspor DJP (Coretax) perlu diverifikasi tim pajak sebelum dipakai.
 */
@RestController
@RequestMapping("/api/fin/tax")
class TaxControllers {

    static final List<String> TYPES = List.of("PPN", "PPH21", "PPH23", "PPH4_2", "PPH22");

    private final JdbcTemplate jdbc;
    private final PermissionService perm;
    private final FinSupport fin;
    private final NumberingService numbering;

    TaxControllers(JdbcTemplate jdbc, PermissionService perm, FinSupport fin, NumberingService numbering) {
        this.jdbc = jdbc;
        this.perm = perm;
        this.fin = fin;
        this.numbering = numbering;
    }

    private static YearMonth ym(String period) {
        if (period == null || !period.matches("\\d{6}")) {
            throw new BusinessException("PERIOD", "Masa pajak wajib berformat YYYYMM");
        }
        return YearMonth.of(Integer.parseInt(period.substring(0, 4)), Integer.parseInt(period.substring(4)));
    }

    private BigDecimal dppLain(BigDecimal base) {
        return base.multiply(fin.paramNum("PPN_DPP_NUM", "11")).divide(fin.paramNum("PPN_DPP_DEN", "12"), 0, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ FIN-60 PPN

    @GetMapping("/ppn")
    public Map<String, Object> ppn(@RequestParam String period) {
        perm.require("FIN-60", Action.VIEW);
        YearMonth ym = ym(period);
        Long plant = UserContext.plantId();
        Date from = Date.valueOf(ym.atDay(1));
        Date to = Date.valueOf(ym.atEndOfMonth());
        List<Map<String, Object>> input = jdbc.queryForList("""
                SELECT i.id, i.doc_no, i.doc_date, i.tax_invoice_no, i.supplier_invoice_no, p.name AS partner, p.npwp, i.subtotal, i.ppn_amount
                FROM fin.ap_invoice i JOIN sys.partner p ON p.id = i.partner_id
                WHERE i.plant_id = ? AND i.status IN ('POSTED','DONE') AND i.ppn_amount > 0 AND i.doc_date BETWEEN ? AND ? ORDER BY i.doc_date""",
                plant, from, to);
        List<Map<String, Object>> imports = jdbc.queryForList("""
                SELECT lc.id, lc.doc_no, lc.doc_date, lc.pib_no AS tax_invoice_no, c.amount AS ppn_amount
                FROM prc.landed_cost lc JOIN prc.lc_charge c ON c.lc_id = lc.id
                WHERE lc.plant_id = ? AND lc.status IN ('POSTED','DONE') AND c.kind = 'PPN_IMPORT' AND lc.doc_date BETWEEN ? AND ?""", plant, from, to);
        List<Map<String, Object>> output = jdbc.queryForList("""
                SELECT i.id, i.doc_no, i.doc_date, i.tax_invoice_no, p.name AS partner, p.npwp, i.subtotal, i.ppn_amount
                FROM fin.ar_invoice i JOIN sys.partner p ON p.id = i.partner_id
                WHERE i.plant_id = ? AND i.status IN ('POSTED','DONE') AND i.ppn_amount > 0 AND i.doc_date BETWEEN ? AND ? ORDER BY i.doc_date""",
                plant, from, to);
        List<Map<String, Object>> returns = jdbc.queryForList("""
                SELECT r.id, r.doc_no, r.doc_date, p.name AS partner, r.subtotal, r.ppn_amount FROM scm.customer_return r JOIN sys.partner p ON p.id = r.partner_id
                WHERE r.plant_id = ? AND r.status IN ('POSTED','DONE') AND r.ppn_amount > 0 AND r.doc_date BETWEEN ? AND ?""", plant, from, to);
        for (List<Map<String, Object>> list : List.of(input, output)) {
            list.forEach(r -> r.put("dpp", dppLain((BigDecimal) r.get("subtotal"))));
        }
        BigDecimal in = sum(input, "ppn_amount").add(sum(imports, "ppn_amount"));
        BigDecimal out = sum(output, "ppn_amount").subtract(sum(returns, "ppn_amount"));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("input", input);
        res.put("imports", imports);
        res.put("output", output);
        res.put("returns", returns);
        res.put("totalInput", in);
        res.put("totalOutput", out);
        res.put("payable", out.subtract(in));
        res.put("missingTaxInvoice", input.stream().filter(r -> blank(r.get("tax_invoice_no"))).count()
                + output.stream().filter(r -> blank(r.get("tax_invoice_no"))).count());
        return res;
    }

    private static boolean blank(Object o) {
        return o == null || o.toString().isBlank();
    }

    private static BigDecimal sum(List<Map<String, Object>> rows, String key) {
        return rows.stream().map(r -> (BigDecimal) r.get(key)).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------ FIN-61 PPh

    @GetMapping("/pph")
    public Map<String, Object> pph(@RequestParam String period) {
        perm.require("FIN-61", Action.VIEW);
        YearMonth ym = ym(period);
        Long plant = UserContext.plantId();
        Date from = Date.valueOf(ym.atDay(1));
        Date to = Date.valueOf(ym.atEndOfMonth());
        List<Map<String, Object>> pph21 = jdbc.queryForList("""
                SELECT e.nik, e.name, e.npwp, s.ptkp_status, s.ter_category, s.taxable_gross, s.pph21, r.doc_no
                FROM hc.payroll_slip s JOIN hc.payroll_run r ON r.id = s.run_id JOIN hc.employee e ON e.id = s.employee_id
                WHERE r.plant_id = ? AND r.period = ? AND r.status IN ('POSTED','DONE') ORDER BY e.nik""", plant, period);
        List<Map<String, Object>> withheld = jdbc.queryForList("""
                SELECT i.id, i.doc_no, i.doc_date, i.supplier_invoice_no, p.name AS partner, p.npwp, t.type AS tax_type, t.code AS tax_code, t.rate,
                       i.subtotal AS dpp, i.pph_amount AS tax,
                       (SELECT w.slip_no FROM fin.withholding_slip w WHERE w.source_doc_type = 'INV-AP' AND w.source_doc_id = i.id AND w.status = 'ISSUED') AS slip_no
                FROM fin.ap_invoice i JOIN sys.partner p ON p.id = i.partner_id JOIN sys.tax_code t ON t.code = i.pph_tax_code
                WHERE i.plant_id = ? AND i.status IN ('POSTED','DONE') AND i.pph_amount > 0 AND i.doc_date BETWEEN ? AND ? ORDER BY i.doc_date""",
                plant, from, to);
        List<Map<String, Object>> pph22 = jdbc.queryForList("""
                SELECT lc.doc_no, lc.doc_date, lc.pib_no, c.amount AS tax FROM prc.landed_cost lc JOIN prc.lc_charge c ON c.lc_id = lc.id
                WHERE lc.plant_id = ? AND lc.status IN ('POSTED','DONE') AND c.kind = 'PPH22_IMPORT' AND lc.doc_date BETWEEN ? AND ?""", plant, from, to);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pph21", pph21);
        res.put("withheld", withheld);
        res.put("pph22", pph22);
        res.put("totals", totals(period, plant));
        return res;
    }

    /** Jumlah pajak per jenis untuk satu masa (dasar SPT masa FIN-62). */
    Map<String, BigDecimal> totals(String period, Long plant) {
        YearMonth ym = ym(period);
        Date from = Date.valueOf(ym.atDay(1));
        Date to = Date.valueOf(ym.atEndOfMonth());
        Map<String, BigDecimal> t = new LinkedHashMap<>();
        BigDecimal out = jdbc.queryForObject("""
                SELECT COALESCE(SUM(ppn_amount), 0) FROM fin.ar_invoice WHERE plant_id = ? AND status IN ('POSTED','DONE') AND doc_date BETWEEN ? AND ?""",
                BigDecimal.class, plant, from, to).subtract(jdbc.queryForObject("""
                SELECT COALESCE(SUM(ppn_amount), 0) FROM scm.customer_return WHERE plant_id = ? AND status IN ('POSTED','DONE') AND doc_date BETWEEN ? AND ?""",
                BigDecimal.class, plant, from, to));
        BigDecimal in = jdbc.queryForObject("""
                SELECT COALESCE(SUM(ppn_amount), 0) FROM fin.ap_invoice WHERE plant_id = ? AND status IN ('POSTED','DONE') AND doc_date BETWEEN ? AND ?""",
                BigDecimal.class, plant, from, to).add(jdbc.queryForObject("""
                SELECT COALESCE(SUM(c.amount), 0) FROM prc.landed_cost lc JOIN prc.lc_charge c ON c.lc_id = lc.id
                WHERE lc.plant_id = ? AND lc.status IN ('POSTED','DONE') AND c.kind = 'PPN_IMPORT' AND lc.doc_date BETWEEN ? AND ?""",
                BigDecimal.class, plant, from, to));
        t.put("PPN", out.subtract(in));
        t.put("PPH21", jdbc.queryForObject("""
                SELECT COALESCE(SUM(total_pph21), 0) FROM hc.payroll_run WHERE plant_id = ? AND period = ? AND status IN ('POSTED','DONE')""",
                BigDecimal.class, plant, period));
        for (String type : List.of("PPH23", "PPH4_2")) {
            t.put(type, jdbc.queryForObject("""
                    SELECT COALESCE(SUM(i.pph_amount), 0) FROM fin.ap_invoice i JOIN sys.tax_code c ON c.code = i.pph_tax_code
                    WHERE i.plant_id = ? AND i.status IN ('POSTED','DONE') AND c.type = ? AND i.doc_date BETWEEN ? AND ?""",
                    BigDecimal.class, plant, type, from, to));
        }
        t.put("PPH22", jdbc.queryForObject("""
                SELECT COALESCE(SUM(c.amount), 0) FROM prc.landed_cost lc JOIN prc.lc_charge c ON c.lc_id = lc.id
                WHERE lc.plant_id = ? AND lc.status IN ('POSTED','DONE') AND c.kind = 'PPH22_IMPORT' AND lc.doc_date BETWEEN ? AND ?""",
                BigDecimal.class, plant, from, to));
        return t;
    }

    // ------------------------------------------------------------------ FIN-62 Bukti potong & SPT masa

    @GetMapping("/periods")
    public List<Map<String, Object>> periods(@RequestParam int year) {
        perm.require("FIN-62", Action.VIEW);
        Long plant = UserContext.plantId();
        List<Map<String, Object>> out = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            String period = "%d%02d".formatted(year, m);
            Map<String, BigDecimal> computed = totals(period, plant);
            for (String type : TYPES) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("period", period);
                r.put("taxType", type);
                r.put("computed", computed.get(type));
                jdbc.query("SELECT * FROM fin.tax_period WHERE plant_id = ? AND period = ? AND tax_type = ?", rs -> {
                    r.put("amount", rs.getBigDecimal("amount"));
                    r.put("billingCode", rs.getString("billing_code"));
                    r.put("ntpn", rs.getString("ntpn"));
                    r.put("paidDate", rs.getDate("paid_date"));
                    r.put("reportedDate", rs.getDate("reported_date"));
                    r.put("bpeNo", rs.getString("bpe_no"));
                    r.put("status", rs.getString("status"));
                    r.put("notes", rs.getString("notes"));
                }, plant, period, type);
                r.putIfAbsent("status", "OPEN");
                out.add(r);
            }
        }
        return out;
    }

    record TaxPeriod(String period, String taxType, BigDecimal amount, String billingCode, String ntpn, LocalDate paidDate,
                     LocalDate reportedDate, String bpeNo, String notes) {
    }

    /** Catat setoran (NTPN) dan pelaporan (BPE) satu jenis pajak per masa; status mengikuti kelengkapan. */
    @PutMapping("/periods")
    @Transactional
    public Map<String, Object> savePeriod(@RequestBody TaxPeriod p) {
        perm.require("FIN-62", Action.EDIT);
        ym(p.period());
        if (!TYPES.contains(p.taxType())) {
            throw new BusinessException("TAX_TYPE", "Jenis pajak tidak dikenal");
        }
        if (p.paidDate() != null && (p.ntpn() == null || p.ntpn().isBlank())) {
            throw new BusinessException("TAX_NTPN", "Tanggal setor wajib disertai NTPN");
        }
        String status = p.reportedDate() != null ? "REPORTED" : p.paidDate() != null ? "PAID" : "OPEN";
        BigDecimal amount = p.amount() != null ? p.amount() : totals(p.period(), UserContext.plantId()).get(p.taxType());
        jdbc.update("""
                INSERT INTO fin.tax_period (plant_id, period, tax_type, amount, billing_code, ntpn, paid_date, reported_date, bpe_no, status, notes, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (plant_id, period, tax_type) DO UPDATE SET amount = EXCLUDED.amount, billing_code = EXCLUDED.billing_code,
                    ntpn = EXCLUDED.ntpn, paid_date = EXCLUDED.paid_date, reported_date = EXCLUDED.reported_date, bpe_no = EXCLUDED.bpe_no,
                    status = EXCLUDED.status, notes = EXCLUDED.notes, updated_at = now(), updated_by = EXCLUDED.updated_by""",
                UserContext.plantId(), p.period(), p.taxType(), amount, p.billingCode(), p.ntpn(),
                p.paidDate() == null ? null : Date.valueOf(p.paidDate()), p.reportedDate() == null ? null : Date.valueOf(p.reportedDate()),
                p.bpeNo(), status, p.notes(), UserContext.userId());
        return Map.of("status", status);
    }

    /** Terbitkan nomor bukti potong PPh 23 / 4(2) untuk faktur supplier terposting di masa ini yang belum punya bukti potong. */
    @PostMapping("/withholding-slips/generate")
    @Transactional
    public Map<String, Object> generateSlips(@RequestParam String period) {
        perm.require("FIN-62", Action.CREATE);
        YearMonth ym = ym(period);
        Long plant = UserContext.plantId();
        int created = 0;
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT i.id, i.doc_no, i.partner_id, p.npwp, c.type, c.rate, i.subtotal, i.pph_amount, i.doc_date
                FROM fin.ap_invoice i JOIN sys.partner p ON p.id = i.partner_id JOIN sys.tax_code c ON c.code = i.pph_tax_code
                WHERE i.plant_id = ? AND i.status IN ('POSTED','DONE') AND i.pph_amount > 0 AND i.doc_date BETWEEN ? AND ?
                  AND NOT EXISTS (SELECT 1 FROM fin.withholding_slip w WHERE w.source_doc_type = 'INV-AP' AND w.source_doc_id = i.id AND w.status = 'ISSUED')
                ORDER BY i.doc_date, i.id""", plant, Date.valueOf(ym.atDay(1)), Date.valueOf(ym.atEndOfMonth()))) {
            jdbc.update("""
                    INSERT INTO fin.withholding_slip (plant_id, tax_type, period, slip_no, partner_id, npwp, source_doc_type, source_doc_id, source_doc_no,
                                                      dpp, rate, tax, issued_date, created_by) VALUES (?, ?, ?, ?, ?, ?, 'INV-AP', ?, ?, ?, ?, ?, ?, ?)""",
                    plant, r.get("type"), period, numbering.next("BPOT", plant, ym.atEndOfMonth()), r.get("partner_id"), r.get("npwp"),
                    r.get("id"), r.get("doc_no"), r.get("subtotal"), r.get("rate"), r.get("pph_amount"), r.get("doc_date"), UserContext.userId());
            created++;
        }
        return Map.of("created", created);
    }

    @GetMapping("/withholding-slips")
    public List<Map<String, Object>> slips(@RequestParam String period) {
        perm.require("FIN-62", Action.VIEW);
        return jdbc.queryForList("""
                SELECT w.*, p.name AS partner FROM fin.withholding_slip w LEFT JOIN sys.partner p ON p.id = w.partner_id
                WHERE w.plant_id = ? AND w.period = ? ORDER BY w.slip_no""", UserContext.plantId(), period);
    }

    // ------------------------------------------------------------------ FIN-63 Rekonsiliasi fiskal

    /** Laba komersial → koreksi fiskal (otomatis: selisih penyusutan; manual: FIN-63) → laba fiskal & estimasi PPh Badan. */
    @GetMapping("/fiscal")
    public Map<String, Object> fiscal(@RequestParam int year) {
        perm.require("FIN-63", Action.VIEW);
        Date from = Date.valueOf(LocalDate.of(year, 1, 1));
        Date to = Date.valueOf(LocalDate.of(year, 12, 31));
        BigDecimal profit = jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.credit - l.debit), 0) FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id
                JOIN fin.account a ON a.id = l.account_id WHERE e.posted_at IS NOT NULL AND a.type IN ('REVENUE','EXPENSE') AND e.doc_date BETWEEN ? AND ?""",
                BigDecimal.class, from, to);
        List<Map<String, Object>> corrections = new ArrayList<>();
        BigDecimal dep = jdbc.queryForObject("""
                SELECT COALESCE(SUM(dl.amount - dl.fiscal_amount), 0) FROM fin.depreciation_line dl JOIN fin.depreciation_run r ON r.id = dl.run_id
                WHERE r.status IN ('POSTED','DONE') AND r.period LIKE ?""", BigDecimal.class, year + "%");
        if (dep.signum() != 0) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("description", "Selisih penyusutan komersial vs fiskal (FIN-40)");
            c.put("kind", dep.signum() > 0 ? "POSITIVE" : "NEGATIVE");
            c.put("category", "WAKTU");
            c.put("amount", dep.abs());
            c.put("auto", true);
            corrections.add(c);
        }
        for (Map<String, Object> m : jdbc.queryForList("""
                SELECT f.description, f.kind, f.category, f.amount, a.code || ' ' || a.name AS account FROM fin.fiscal_correction f
                LEFT JOIN fin.account a ON a.id = f.account_id WHERE f.year = ? AND f.active ORDER BY f.id""", year)) {
            m.put("auto", false);
            corrections.add(m);
        }
        BigDecimal fiscal = profit;
        for (Map<String, Object> c : corrections) {
            BigDecimal a = (BigDecimal) c.get("amount");
            fiscal = "POSITIVE".equals(c.get("kind")) ? fiscal.add(a) : fiscal.subtract(a);
        }
        BigDecimal rate = fin.paramNum("PPH_BADAN_RATE", "0.22");
        BigDecimal taxBase = fiscal.max(BigDecimal.ZERO).divide(BigDecimal.valueOf(1000), 0, RoundingMode.DOWN).multiply(BigDecimal.valueOf(1000));
        BigDecimal tax = taxBase.multiply(rate).setScale(0, RoundingMode.HALF_UP);
        BigDecimal credit = jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.debit - l.credit), 0) FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id
                JOIN fin.account a ON a.id = l.account_id WHERE e.posted_at IS NOT NULL AND a.code = '1225' AND e.doc_date BETWEEN ? AND ?""",
                BigDecimal.class, from, to);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commercialProfit", profit);
        out.put("corrections", corrections);
        out.put("fiscalProfit", fiscal);
        out.put("taxBase", taxBase);
        out.put("rate", rate);
        out.put("tax", tax);
        out.put("prepaidPph22", credit);
        out.put("payable", tax.subtract(credit));
        out.put("note", "Estimasi; fasilitas Pasal 31E, kompensasi rugi, dan kredit PPh 23/25 dilengkapi tim pajak saat SPT Tahunan.");
        return out;
    }
}
