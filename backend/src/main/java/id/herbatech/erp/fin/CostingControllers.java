package id.herbatech.erp.fin;

import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.domain.DocStatus;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.period.PeriodLockService;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import id.herbatech.erp.shared.web.MasterController;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

interface OverheadAllocationRepository extends DocumentRepository<OverheadAllocation> {
}

/**
 * Perhitungan biaya produksi: biaya standar dari BOM (FIN-52), biaya aktual per batch (FIN-53), tarif per jam.
 * Data BOM, stok, dan jam kerja dibaca dari skema SCM/PRE (FIN tidak bergantung pada modul tersebut).
 */
@Service
class CostingService {

    record BatchCost(Long woId, String woNo, String batchNo, Long itemId, String itemCode, String itemName, String status,
                     BigDecimal qtyGood, BigDecimal material, BigDecimal laborHours, BigDecimal labor, BigDecimal overhead, BigDecimal total,
                     BigDecimal unitCost, BigDecimal stdUnitCost, BigDecimal fgValue, BigDecimal variance, Long costCenterId, LocalDate date,
                     boolean closed, String journalNo) {
    }

    private final JdbcTemplate jdbc;

    CostingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Tarif berlaku (tenaga kerja, overhead per jam) cost center pada tanggal. */
    BigDecimal[] rates(Long plantId, Long costCenterId, LocalDate on) {
        return jdbc.query("""
                SELECT labor_rate, overhead_rate FROM fin.cost_rate WHERE plant_id = ? AND cost_center_id = ? AND active AND valid_from <= ?
                ORDER BY valid_from DESC, id DESC LIMIT 1""", rs -> rs.next() ? new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2)}
                : new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}, plantId, costCenterId, Date.valueOf(on));
    }

    /** Harga bahan untuk biaya standar: rata-rata stok saat ini, lalu harga kontrak termurah, lalu harga PO terakhir. */
    BigDecimal materialPrice(Long itemId, Long plantId, LocalDate on) {
        BigDecimal avg = jdbc.queryForObject("""
                SELECT CASE WHEN SUM(qty) > 0 THEN SUM(qty * unit_cost) / SUM(qty) ELSE 0 END FROM scm.stock_quant WHERE item_id = ?""",
                BigDecimal.class, itemId);
        if (avg.signum() > 0) {
            return avg;
        }
        BigDecimal std = jdbc.queryForList("SELECT total FROM fin.std_cost WHERE plant_id = ? AND item_id = ? AND current", BigDecimal.class, plantId, itemId)
                .stream().findFirst().orElse(BigDecimal.ZERO);
        if (std.signum() > 0) {
            return std;
        }
        return jdbc.queryForList("""
                SELECT price FROM prc.price_list WHERE item_id = ? AND active AND valid_from <= ? AND (valid_until IS NULL OR valid_until >= ?)
                ORDER BY price LIMIT 1""", BigDecimal.class, itemId, Date.valueOf(on), Date.valueOf(on)).stream().findFirst()
                .orElseGet(() -> jdbc.queryForList("""
                        SELECT l.unit_price * p.exchange_rate FROM prc.po_line l JOIN prc.po p ON p.id = l.po_id
                        WHERE l.item_id = ? AND p.status IN ('APPROVED','DONE') ORDER BY p.doc_date DESC LIMIT 1""", BigDecimal.class, itemId)
                        .stream().findFirst().orElse(BigDecimal.ZERO));
    }

    /** FIN-52: biaya standar per unit produk = Σ komponen BOM × harga + jam standar × (tarif TK + tarif overhead), dibagi jumlah dasar. */
    Map<String, Object> calculateStandard(Long plantId, Long itemId, LocalDate on, Set<Long> visiting) {
        if (!visiting.add(itemId)) {
            throw new BusinessException("STD_LOOP", "BOM melingkar saat menghitung biaya standar");
        }
        Map<String, Object> bom = jdbc.queryForList("""
                SELECT b.id, b.base_qty, b.std_hours, pp.default_line_id, l.cost_center_id FROM scm.bom b
                LEFT JOIN pre.product_param pp ON pp.item_id = b.item_id LEFT JOIN pre.line l ON l.id = pp.default_line_id
                WHERE b.item_id = ? AND b.plant_id = ? AND b.status = 'POSTED' ORDER BY b.revision DESC LIMIT 1""", itemId, plantId)
                .stream().findFirst().orElseThrow(() -> new BusinessException("STD_BOM", "Produk belum punya BOM berlaku (SCM-05)"));
        BigDecimal base = (BigDecimal) bom.get("base_qty");
        BigDecimal material = BigDecimal.ZERO;
        StringBuilder detail = new StringBuilder();
        for (Map<String, Object> c : jdbc.queryForList("""
                SELECT bl.component_item_id, bl.qty, bl.scrap_pct, i.code, i.type FROM scm.bom_line bl JOIN sys.item i ON i.id = bl.component_item_id
                WHERE bl.bom_id = ? ORDER BY bl.line_no""", bom.get("id"))) {
            Long comp = ((Number) c.get("component_item_id")).longValue();
            BigDecimal qty = ((BigDecimal) c.get("qty")).multiply(BigDecimal.ONE.add(((BigDecimal) c.get("scrap_pct")).divide(BigDecimal.valueOf(100), 10,
                    RoundingMode.HALF_UP)));
            BigDecimal price = "WIP".equals(c.get("type")) && hasBom(plantId, comp)
                    ? (BigDecimal) calculateStandard(plantId, comp, on, visiting).get("total") : materialPrice(comp, plantId, on);
            BigDecimal value = qty.multiply(price);
            material = material.add(value);
            detail.append(c.get("code")).append(": ").append(qty.stripTrailingZeros().toPlainString()).append(" × ")
                    .append(price.setScale(2, RoundingMode.HALF_UP).toPlainString()).append("; ");
        }
        BigDecimal hours = (BigDecimal) bom.get("std_hours");
        BigDecimal[] rate = bom.get("cost_center_id") == null ? new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}
                : rates(plantId, ((Number) bom.get("cost_center_id")).longValue(), on);
        BigDecimal unitMat = material.divide(base, 6, RoundingMode.HALF_UP);
        BigDecimal unitLabor = hours.multiply(rate[0]).divide(base, 6, RoundingMode.HALF_UP);
        BigDecimal unitOh = hours.multiply(rate[1]).divide(base, 6, RoundingMode.HALF_UP);
        BigDecimal total = unitMat.add(unitLabor).add(unitOh);
        jdbc.update("UPDATE fin.std_cost SET current = FALSE WHERE plant_id = ? AND item_id = ? AND current", plantId, itemId);
        jdbc.update("""
                INSERT INTO fin.std_cost (plant_id, item_id, bom_id, material, labor, overhead, total, calc_date, detail, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", plantId, itemId, bom.get("id"), unitMat, unitLabor, unitOh, total, Date.valueOf(on),
                detail.toString(), UserContext.currentOptional().map(u -> u.id()).orElse(null));
        visiting.remove(itemId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("itemId", itemId);
        out.put("material", unitMat);
        out.put("labor", unitLabor);
        out.put("overhead", unitOh);
        out.put("total", total);
        return out;
    }

    private boolean hasBom(Long plantId, Long itemId) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM scm.bom WHERE item_id = ? AND plant_id = ? AND status = 'POSTED'", Long.class, itemId, plantId);
        return n != null && n > 0;
    }

    /** FIN-53: biaya aktual batch = bahan bersih (serah − retur) + jam kerja × tarif TK + jam × tarif overhead. */
    BatchCost batch(Long woId) {
        Map<String, Object> w = jdbc.queryForList("""
                SELECT w.id, w.doc_no, w.batch_no, w.status, w.plant_id, w.product_item_id, i.code, i.name, l.cost_center_id,
                       COALESCE((w.finished_at AT TIME ZONE 'Asia/Jakarta')::date, w.planned_end, w.planned_start) AS d
                FROM pre.work_order w JOIN sys.item i ON i.id = w.product_item_id JOIN pre.line l ON l.id = w.line_id WHERE w.id = ?""", woId)
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Work order", woId));
        Long plant = ((Number) w.get("plant_id")).longValue();
        Long cc = ((Number) w.get("cost_center_id")).longValue();
        LocalDate date = ((Date) w.get("d")).toLocalDate();
        BigDecimal material = jdbc.queryForObject("""
                SELECT COALESCE((SELECT SUM(m.qty * m.unit_cost) FROM scm.stock_move m JOIN pre.material_request r
                                 ON m.ref_doc_type = 'MR' AND m.ref_doc_id = r.id WHERE r.wo_id = ?), 0)
                     - COALESCE((SELECT SUM(m.qty * m.unit_cost) FROM scm.stock_move m JOIN pre.material_return r
                                 ON m.ref_doc_type = 'MRT' AND m.ref_doc_id = r.id WHERE r.wo_id = ?), 0)""", BigDecimal.class, woId, woId);
        BigDecimal hours = jdbc.queryForObject("SELECT COALESCE(SUM(hours), 0) FROM pre.labor_entry WHERE wo_id = ? AND active", BigDecimal.class, woId);
        BigDecimal[] rate = rates(plant, cc, date);
        BigDecimal labor = hours.multiply(rate[0]).setScale(2, RoundingMode.HALF_UP);
        BigDecimal overhead = hours.multiply(rate[1]).setScale(2, RoundingMode.HALF_UP);
        material = material.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = material.add(labor).add(overhead);
        Map<String, Object> out = jdbc.queryForList("""
                SELECT COALESCE(SUM(qty_good), 0) AS qty, COALESCE(SUM(total_cost), 0) AS fg FROM pre.production_output WHERE wo_id = ? AND status = 'POSTED'""",
                woId).getFirst();
        BigDecimal qty = (BigDecimal) out.get("qty");
        BigDecimal fg = (BigDecimal) out.get("fg");
        BigDecimal std = jdbc.queryForList("SELECT total FROM fin.std_cost WHERE plant_id = ? AND item_id = ? AND current", BigDecimal.class, plant,
                w.get("product_item_id")).stream().findFirst().orElse(BigDecimal.ZERO);
        Map<String, Object> closed = jdbc.queryForList("SELECT closed_at, journal_doc_no FROM fin.batch_cost WHERE wo_id = ?", woId).stream().findFirst()
                .orElse(null);
        return new BatchCost(woId, (String) w.get("doc_no"), (String) w.get("batch_no"), ((Number) w.get("product_item_id")).longValue(),
                (String) w.get("code"), (String) w.get("name"), (String) w.get("status"), qty, material, hours, labor, overhead, total,
                qty.signum() == 0 ? BigDecimal.ZERO : total.divide(qty, 6, RoundingMode.HALF_UP), std, fg, total.subtract(fg), cc, date,
                closed != null && closed.get("closed_at") != null, closed == null ? null : (String) closed.get("journal_doc_no"));
    }
}

@RestController
@RequestMapping("/api/fin")
class CostingController {

    private final CostingService costing;
    private final JournalPostingService journals;
    private final PeriodLockService periods;
    private final PermissionService perm;
    private final TimeService time;
    private final JdbcTemplate jdbc;

    CostingController(CostingService costing, JournalPostingService journals, PeriodLockService periods, PermissionService perm, TimeService time,
                      JdbcTemplate jdbc) {
        this.costing = costing;
        this.journals = journals;
        this.periods = periods;
        this.perm = perm;
        this.time = time;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ FIN-52 Biaya standar

    @GetMapping("/std-costs")
    public List<Map<String, Object>> standards() {
        perm.require("FIN-52", Action.VIEW);
        return jdbc.queryForList("""
                SELECT i.id AS item_id, i.code, i.name, i.type, u.code AS uom, b.doc_no AS bom_no, b.revision,
                       s.material, s.labor, s.overhead, s.total, s.calc_date, s.detail
                FROM scm.bom b JOIN sys.item i ON i.id = b.item_id JOIN sys.uom u ON u.id = i.uom_id
                LEFT JOIN fin.std_cost s ON s.item_id = b.item_id AND s.plant_id = b.plant_id AND s.current
                WHERE b.plant_id = ? AND b.status = 'POSTED' ORDER BY i.code""", UserContext.plantId());
    }

    record CalcRequest(Long itemId) {
    }

    /** Hitung ulang biaya standar satu produk (atau semua produk ber-BOM bila itemId kosong). */
    @PostMapping("/std-costs/calculate")
    @Transactional
    public List<Map<String, Object>> calculate(@RequestBody CalcRequest req) {
        perm.require("FIN-52", Action.CREATE);
        Long plant = UserContext.plantId();
        List<Long> items = req.itemId() != null ? List.of(req.itemId())
                : jdbc.queryForList("SELECT DISTINCT item_id FROM scm.bom WHERE plant_id = ? AND status = 'POSTED'", Long.class, plant);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long i : items) {
            out.add(costing.calculateStandard(plant, i, time.today(), new HashSet<>()));
        }
        return out;
    }

    // ------------------------------------------------------------------ FIN-53 Biaya aktual per batch

    @GetMapping("/batch-costs")
    public List<CostingService.BatchCost> batches(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        perm.require("FIN-53", Action.VIEW);
        return jdbc.queryForList("""
                SELECT id FROM pre.work_order WHERE plant_id = ? AND status IN ('APPROVED','DONE') AND planned_start BETWEEN ? AND ?
                ORDER BY planned_start DESC""", Long.class, UserContext.plantId(), Date.valueOf(from), Date.valueOf(to))
                .stream().map(costing::batch).toList();
    }

    /**
     * Tutup biaya batch (WO selesai): serap tenaga kerja & overhead ke WIP, lalu selisih biaya aktual vs nilai barang jadi
     * (standar) dibukukan ke selisih biaya produksi sehingga saldo WIP batch nol.
     */
    @PostMapping("/batch-costs/{woId}/close")
    @Transactional
    public CostingService.BatchCost close(@PathVariable Long woId) {
        perm.require("FIN-53", Action.POST);
        CostingService.BatchCost b = costing.batch(woId);
        if (!"DONE".equals(b.status())) {
            throw new BusinessException("BATCH_OPEN", "Work order belum selesai (hasil produksi belum diposting)");
        }
        if (b.closed()) {
            throw new BusinessException("BATCH_CLOSED", "Biaya batch ini sudah ditutup");
        }
        LocalDate date = time.today();
        periods.assertDateAllowed("COST", date);
        String desc = "Biaya batch " + b.batchNo() + " (" + b.woNo() + ")";
        Long wip = journals.accountId("1303");
        List<JournalPostingService.IdLine> lines = new ArrayList<>();
        lines.add(new JournalPostingService.IdLine(wip, b.costCenterId(), desc + " · tenaga kerja", b.labor(), null));
        lines.add(new JournalPostingService.IdLine(journals.accountId("5301"), null, desc + " · tenaga kerja", null, b.labor()));
        lines.add(new JournalPostingService.IdLine(wip, b.costCenterId(), desc + " · overhead", b.overhead(), null));
        lines.add(new JournalPostingService.IdLine(journals.accountId("5302"), null, desc + " · overhead", null, b.overhead()));
        BigDecimal v = b.variance().setScale(2, RoundingMode.HALF_UP);
        if (v.signum() != 0) {
            lines.add(new JournalPostingService.IdLine(journals.accountId("5204"), b.costCenterId(), desc + " · selisih",
                    v.signum() > 0 ? v : null, v.signum() < 0 ? v.negate() : null));
            lines.add(new JournalPostingService.IdLine(wip, b.costCenterId(), desc + " · selisih", v.signum() < 0 ? v.negate() : null,
                    v.signum() > 0 ? v : null));
        }
        String jno = null;
        if (lines.stream().anyMatch(l -> (l.debit() != null && l.debit().signum() != 0))) {
            jno = journals.postIds(UserContext.plantId(), date, lines, "WO", woId, b.woNo(), desc).getDocNo();
        }
        jdbc.update("""
                INSERT INTO fin.batch_cost (plant_id, wo_id, item_id, batch_no, qty_good, material, labor_hours, labor, overhead, total, unit_cost,
                                            std_unit_cost, fg_value, variance, closed_at, closed_by, journal_doc_no)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, ?)""",
                UserContext.plantId(), woId, b.itemId(), b.batchNo(), b.qtyGood(), b.material(), b.laborHours(), b.labor(), b.overhead(), b.total(),
                b.unitCost(), b.stdUnitCost(), b.fgValue(), v, UserContext.userId(), jno);
        return costing.batch(woId);
    }

    // ------------------------------------------------------------------ FIN-54 Valuasi persediaan & HPP

    /** Nilai persediaan per item (rata-rata tertimbang) dan rekonsiliasi dengan saldo akun persediaan GL. */
    @GetMapping("/valuation")
    public Map<String, Object> valuation() {
        perm.require("FIN-54", Action.VIEW);
        Long plant = UserContext.plantId();
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT i.id, i.code, i.name, i.type, u.code AS uom, SUM(q.qty) AS qty, SUM(q.qty * q.unit_cost) AS value,
                       CASE WHEN SUM(q.qty) > 0 THEN SUM(q.qty * q.unit_cost) / SUM(q.qty) ELSE 0 END AS avg_cost
                FROM scm.stock_quant q JOIN sys.item i ON i.id = q.item_id JOIN sys.uom u ON u.id = i.uom_id
                JOIN sys.location loc ON loc.id = q.location_id JOIN sys.warehouse w ON w.id = loc.warehouse_id
                WHERE w.plant_id = ? AND q.qty > 0 GROUP BY i.id, i.code, i.name, i.type, u.code ORDER BY i.type, i.code""", plant);
        List<Map<String, Object>> recon = jdbc.queryForList("""
                SELECT a.code, a.name, p.key, COALESCE(gl.bal, 0) AS gl,
                       COALESCE((SELECT SUM(q.qty * q.unit_cost) FROM scm.stock_quant q JOIN sys.item i ON i.id = q.item_id
                                 JOIN sys.location loc ON loc.id = q.location_id JOIN sys.warehouse w ON w.id = loc.warehouse_id
                                 WHERE w.plant_id = ? AND i.type = substring(p.key from 9)), 0) AS stock
                FROM fin.fin_param p JOIN fin.account a ON a.code = p.value
                LEFT JOIN (SELECT l.account_id, SUM(l.debit - l.credit) AS bal FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id
                           WHERE e.posted_at IS NOT NULL AND e.plant_id = ? GROUP BY l.account_id) gl ON gl.account_id = a.id
                WHERE p.key LIKE 'INV_ACC_%' ORDER BY a.code""", plant, plant);
        return Map.of("items", items, "reconciliation", recon);
    }

    /** HPP penjualan per produk (dari surat jalan terposting) untuk satu bulan. */
    @GetMapping("/cogs")
    public List<Map<String, Object>> cogs(@RequestParam int year, @RequestParam int month) {
        perm.require("FIN-54", Action.VIEW);
        YearMonth ym = YearMonth.of(year, month);
        return jdbc.queryForList("""
                SELECT i.code, i.name, SUM(dl.qty) AS qty, SUM(dl.qty * dl.unit_cost) AS cogs, SUM(dl.qty * dl.unit_price) AS revenue
                FROM scm.delivery_line dl JOIN scm.delivery d ON d.id = dl.delivery_id JOIN sys.item i ON i.id = dl.item_id
                WHERE d.plant_id = ? AND d.status IN ('POSTED','DONE') AND d.doc_date BETWEEN ? AND ?
                GROUP BY i.code, i.name ORDER BY cogs DESC""", UserContext.plantId(), Date.valueOf(ym.atDay(1)), Date.valueOf(ym.atEndOfMonth()));
    }
}

@RestController
@RequestMapping("/api/fin/cost-rates")
class CostRateController extends MasterController<CostRate> {
    CostRateController(CostRateRepository r, PermissionService p) { super(r, p, CostRate.class); }
    @Override protected String menuCode() { return "FIN-52"; }
    @Override protected List<String> searchFields() { return List.of("source"); }
    @Override protected Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "validFrom"); }
}

@RestController
@RequestMapping("/api/fin/fiscal-corrections")
class FiscalCorrectionController extends MasterController<FiscalCorrection> {
    FiscalCorrectionController(FiscalCorrectionRepository r, PermissionService p) { super(r, p, FiscalCorrection.class); }
    @Override protected String menuCode() { return "FIN-63"; }
    @Override protected List<String> searchFields() { return List.of("description"); }
    @Override protected Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "year"); }
}

// =====================================================================================================================
// FIN-55 Alokasi overhead
// =====================================================================================================================

@Component
class OverheadAllocationHandler implements DocumentHandler<OverheadAllocation> {

    private final OverheadAllocationRepository repo;
    private final JournalPostingService journals;
    private final JdbcTemplate jdbc;

    OverheadAllocationHandler(OverheadAllocationRepository repo, JournalPostingService journals, JdbcTemplate jdbc) {
        this.repo = repo;
        this.journals = journals;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "OHA"; }
    @Override public String periodModule() { return "COST"; }
    @Override public OverheadAllocation load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Alokasi overhead", id)); }
    @Override public OverheadAllocation save(OverheadAllocation doc) { return repo.save(doc); }
    @Override public boolean autoPost(OverheadAllocation doc) { return true; }
    @Override public BigDecimal amount(OverheadAllocation d) { return d.getTotal(); }

    @Override
    public String summary(OverheadAllocation d) {
        return "Periode %s · dasar %s · %d baris".formatted(d.getPeriod(), "OUTPUT".equals(d.getBasis()) ? "output" : "jam kerja", d.getLines().size());
    }

    @Override
    public void validateSubmit(OverheadAllocation d) {
        if (d.getLines().isEmpty()) {
            throw new BusinessException("OHA_LINES", "Hitung alokasi dulu (tombol \"Hitung alokasi\")");
        }
        Long dup = jdbc.queryForObject("""
                SELECT count(*) FROM fin.overhead_alloc WHERE plant_id = ? AND period = ? AND id <> ? AND status IN ('SUBMITTED','APPROVED','POSTED')""",
                Long.class, d.getPlantId(), d.getPeriod(), d.getId());
        if (dup != null && dup > 0) {
            throw new BusinessException("OHA_DUP", "Alokasi overhead periode " + d.getPeriod() + " sudah ada");
        }
    }

    /** Beban pindah dari cost center pendukung ke cost center lini; tarif overhead aktual per jam lini diperbarui untuk FIN-53. */
    @Override
    public void onPost(OverheadAllocation d) {
        validateSubmit(d);
        List<JournalPostingService.IdLine> lines = new ArrayList<>();
        String desc = "Alokasi overhead " + d.getPeriod();
        for (OverheadAllocation.Line l : d.getLines()) {
            lines.add(new JournalPostingService.IdLine(l.getAccountId(), l.getTargetCcId(), desc, l.getAmount(), null));
            lines.add(new JournalPostingService.IdLine(l.getAccountId(), l.getSourceCcId(), desc, null, l.getAmount()));
        }
        YearMonth ym = YearMonth.of(Integer.parseInt(d.getPeriod().substring(0, 4)), Integer.parseInt(d.getPeriod().substring(4)));
        journals.postIds(d.getPlantId(), ym.atEndOfMonth().isBefore(d.getDocDate()) ? ym.atEndOfMonth() : d.getDocDate(), lines, "OHA", d.getId(),
                d.getDocNo(), desc);
        for (Long cc : d.getLines().stream().map(OverheadAllocation.Line::getTargetCcId).distinct().toList()) {
            BigDecimal hours = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(le.hours), 0) FROM pre.labor_entry le JOIN pre.work_order w ON w.id = le.wo_id JOIN pre.line l ON l.id = w.line_id
                    WHERE l.cost_center_id = ? AND le.active AND le.work_date BETWEEN ? AND ?""", BigDecimal.class, cc,
                    Date.valueOf(ym.atDay(1)), Date.valueOf(ym.atEndOfMonth()));
            if (hours.signum() <= 0) {
                continue;
            }
            BigDecimal oh = d.getLines().stream().filter(l -> l.getTargetCcId().equals(cc)).map(OverheadAllocation.Line::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal labor = jdbc.queryForList("""
                    SELECT labor_rate FROM fin.cost_rate WHERE plant_id = ? AND cost_center_id = ? AND active AND valid_from <= ?
                    ORDER BY valid_from DESC LIMIT 1""", BigDecimal.class, d.getPlantId(), cc, Date.valueOf(ym.atEndOfMonth())).stream().findFirst()
                    .orElse(BigDecimal.ZERO);
            jdbc.update("""
                    INSERT INTO fin.cost_rate (plant_id, cost_center_id, valid_from, labor_rate, overhead_rate, source) VALUES (?, ?, ?, ?, ?, 'ALLOCATION')""",
                    d.getPlantId(), cc, Date.valueOf(ym.atDay(1)), labor, oh.divide(hours, 2, RoundingMode.HALF_UP));
        }
    }

    @Override
    public OverheadAllocation reverse(OverheadAllocation d, LocalDate date, String reason) {
        journals.reverseFor("OHA", d.getId(), date, reason);
        return d;
    }
}

@RestController
@RequestMapping("/api/fin/overhead-allocations")
class OverheadAllocationController extends DocumentApi<OverheadAllocation> {

    private final OverheadAllocationHandler handler;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;

    OverheadAllocationController(OverheadAllocationHandler handler, OverheadAllocationRepository repo, Support support, JdbcTemplate jdbc,
                                 PermissionService perm) {
        super(handler, repo, support, OverheadAllocation.class);
        this.handler = handler;
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("total");
    }

    @Override
    protected void beforeSave(OverheadAllocation d, boolean isNew) {
        require(d.getPeriod() != null && d.getPeriod().matches("\\d{6}"), "OHA_PERIOD", "Periode wajib diisi (format YYYYMM)");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, OverheadAllocation d) {
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                m.put("sourceCc", jdbc.queryForObject("SELECT code || ' ' || name FROM sys.cost_center WHERE id = ?", String.class,
                        ((Number) m.get("sourceCcId")).longValue()));
                m.put("targetCc", jdbc.queryForObject("SELECT code || ' ' || name FROM sys.cost_center WHERE id = ?", String.class,
                        ((Number) m.get("targetCcId")).longValue()));
                m.put("account", jdbc.queryForObject("SELECT code || ' ' || name FROM fin.account WHERE id = ?", String.class,
                        ((Number) m.get("accountId")).longValue()));
            }
        }
    }

    /**
     * Hitung alokasi: beban utilitas, pemeliharaan, penyusutan (6201–6203) di cost center selain lini produksi dibagi ke
     * cost center lini menurut jam kerja (PRE-12) atau qty output (PRE-08) periode itu.
     */
    @PostMapping("/{id}/compute")
    @Transactional
    public Envelope compute(@PathVariable Long id) {
        perm.require("FIN-55", Action.CREATE);
        OverheadAllocation d = handler.load(id);
        workflow().assertEditable(handler, d);
        YearMonth ym = YearMonth.of(Integer.parseInt(d.getPeriod().substring(0, 4)), Integer.parseInt(d.getPeriod().substring(4)));
        Date from = Date.valueOf(ym.atDay(1));
        Date to = Date.valueOf(ym.atEndOfMonth());
        List<Map<String, Object>> targets = jdbc.queryForList("""
                SELECT l.cost_center_id AS cc,
                       COALESCE(SUM(CASE WHEN ? = 'OUTPUT' THEN (SELECT COALESCE(SUM(o.qty_good), 0) FROM pre.production_output o JOIN pre.work_order w
                                         ON w.id = o.wo_id WHERE w.line_id = l.id AND o.status = 'POSTED' AND o.doc_date BETWEEN ? AND ?)
                                    ELSE (SELECT COALESCE(SUM(le.hours), 0) FROM pre.labor_entry le JOIN pre.work_order w ON w.id = le.wo_id
                                          WHERE w.line_id = l.id AND le.active AND le.work_date BETWEEN ? AND ?) END), 0) AS basis
                FROM pre.line l WHERE l.plant_id = ? AND l.active GROUP BY l.cost_center_id""",
                d.getBasis(), from, to, from, to, d.getPlantId());
        require(!targets.isEmpty(), "OHA_LINES", "Belum ada lini produksi (PRE-99)");
        Set<Long> lineCcs = new HashSet<>();
        BigDecimal basisTotal = BigDecimal.ZERO;
        for (Map<String, Object> t : targets) {
            lineCcs.add(((Number) t.get("cc")).longValue());
            basisTotal = basisTotal.add((BigDecimal) t.get("basis"));
        }
        List<Map<String, Object>> sources = jdbc.queryForList("""
                SELECT l.cost_center_id AS cc, l.account_id, SUM(l.debit - l.credit) AS amount
                FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id JOIN fin.account a ON a.id = l.account_id
                WHERE e.posted_at IS NOT NULL AND e.plant_id = ? AND e.doc_date BETWEEN ? AND ? AND a.code IN ('6201','6202','6203')
                  AND l.cost_center_id IS NOT NULL AND e.source_doc_type IS DISTINCT FROM 'OHA'
                GROUP BY l.cost_center_id, l.account_id HAVING SUM(l.debit - l.credit) > 0""", d.getPlantId(), from, to);
        d.getLines().clear();
        short no = 1;
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> s : sources) {
            Long src = ((Number) s.get("cc")).longValue();
            if (lineCcs.contains(src)) {
                continue;
            }
            BigDecimal amount = (BigDecimal) s.get("amount");
            BigDecimal left = amount;
            for (int k = 0; k < targets.size(); k++) {
                Map<String, Object> t = targets.get(k);
                BigDecimal basis = (BigDecimal) t.get("basis");
                BigDecimal share = basisTotal.signum() == 0 ? BigDecimal.ONE.divide(BigDecimal.valueOf(targets.size()), 10, RoundingMode.HALF_UP)
                        : basis.divide(basisTotal, 10, RoundingMode.HALF_UP);
                BigDecimal part = k == targets.size() - 1 ? left : amount.multiply(share).setScale(2, RoundingMode.HALF_UP);
                left = left.subtract(part);
                if (part.signum() == 0) {
                    continue;
                }
                OverheadAllocation.Line l = new OverheadAllocation.Line();
                l.setAllocation(d);
                l.setLineNo(no++);
                l.setSourceCcId(src);
                l.setAccountId(((Number) s.get("account_id")).longValue());
                l.setTargetCcId(((Number) t.get("cc")).longValue());
                l.setBasisQty(basis);
                l.setSharePct(share.multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP));
                l.setAmount(part);
                d.getLines().add(l);
                total = total.add(part);
            }
        }
        d.setTotal(total);
        handler.save(d);
        workflow().touched(handler, d);
        return envelope(handler.load(id));
    }
}
