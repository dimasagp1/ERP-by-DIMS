package id.herbatech.erp.scm;

import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.meta.DashboardProvider;
import id.herbatech.erp.shared.meta.KpiProvider;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import id.herbatech.erp.shared.web.LookupSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
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

/**
 * Warehouse & inventory control: status lot (SCM-21), putaway (SCM-22), terima barang jadi (SCM-24),
 * kedaluwarsa & slow moving (SCM-44), penelusuran lot (SCM-45), laporan supply chain (SCM-90).
 */
@RestController
@RequestMapping("/api/scm")
class WarehouseControllers {

    private final JdbcTemplate jdbc;
    private final PermissionService perm;
    private final InventoryService inventory;
    private final TimeService time;

    WarehouseControllers(JdbcTemplate jdbc, PermissionService perm, InventoryService inventory, TimeService time) {
        this.jdbc = jdbc;
        this.perm = perm;
        this.inventory = inventory;
        this.time = time;
    }

    // ------------------------------------------------------------------ SCM-21 Status stok & karantina

    @GetMapping("/lots")
    public List<Map<String, Object>> lots(@RequestParam(required = false) String status, @RequestParam(required = false) String q) {
        perm.require("SCM-21", Action.VIEW);
        List<Object> args = new ArrayList<>(List.of(UserContext.plantId()));
        StringBuilder sql = new StringBuilder("""
                SELECT l.id, l.lot_no, l.supplier_lot, l.qc_status, l.mfg_date, l.exp_date, l.source_doc, l.created_at,
                       i.id AS item_id, i.code AS item_code, i.name AS item_name, i.type AS item_type, u.code AS uom,
                       SUM(q.qty) AS qty, SUM(q.qty) FILTER (WHERE loc.is_quarantine) AS qty_quarantine,
                       string_agg(DISTINCT w.code || '/' || loc.bin_code, ', ') AS locations
                FROM scm.lot l JOIN sys.item i ON i.id = l.item_id JOIN sys.uom u ON u.id = i.uom_id
                JOIN scm.stock_quant q ON q.lot_id = l.id AND q.qty > 0
                JOIN sys.location loc ON loc.id = q.location_id JOIN sys.warehouse w ON w.id = loc.warehouse_id
                WHERE w.plant_id = ?""");
        if (status != null && !status.isBlank()) {
            sql.append(" AND l.qc_status = ?");
            args.add(status);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (l.lot_no ILIKE ? OR l.supplier_lot ILIKE ? OR i.code ILIKE ? OR i.name ILIKE ?)");
            for (int k = 0; k < 4; k++) {
                args.add("%" + q.trim() + "%");
            }
        }
        sql.append(" GROUP BY l.id, i.id, u.code ORDER BY l.qc_status = 'QUARANTINE' DESC, l.exp_date NULLS LAST, l.lot_no LIMIT 1000");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    // ------------------------------------------------------------------ SCM-22 Putaway

    /** Stok Released (atau tidak dilacak lot) yang masih di lokasi karantina, dengan saran bin sesuai kelas suhu/B3. */
    @GetMapping("/putaway")
    public List<Map<String, Object>> putawayCandidates() {
        perm.require("SCM-22", Action.VIEW);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT q.item_id, q.lot_id, q.location_id, q.qty, i.code AS item_code, i.name AS item_name, i.storage_class, u.code AS uom,
                       l.lot_no, l.exp_date, l.qc_status, w.id AS warehouse_id, w.code || '/' || loc.bin_code AS from_label
                FROM scm.stock_quant q JOIN sys.location loc ON loc.id = q.location_id JOIN sys.warehouse w ON w.id = loc.warehouse_id
                JOIN sys.item i ON i.id = q.item_id JOIN sys.uom u ON u.id = i.uom_id LEFT JOIN scm.lot l ON l.id = q.lot_id
                WHERE w.plant_id = ? AND loc.is_quarantine AND q.qty > 0 AND (l.id IS NULL OR l.qc_status = 'RELEASED')
                ORDER BY l.exp_date NULLS LAST""", UserContext.plantId());
        for (Map<String, Object> r : rows) {
            String cls = r.get("storage_class") == null ? "AMBIENT" : (String) r.get("storage_class");
            jdbc.query("""
                    SELECT l.id, w.code || '/' || l.bin_code FROM sys.location l JOIN sys.warehouse w ON w.id = l.warehouse_id
                    LEFT JOIN scm.stock_quant q ON q.location_id = l.id AND q.qty > 0
                    WHERE l.warehouse_id = ? AND l.active AND NOT l.is_quarantine
                      AND (CASE WHEN ? = 'B3' THEN l.is_b3 ELSE NOT l.is_b3 AND l.temp_class = ? END)
                    GROUP BY l.id, w.code, l.bin_code ORDER BY COUNT(q.id), l.bin_code LIMIT 1""", rs -> {
                if (rs.next()) {
                    r.put("suggest_location_id", rs.getLong(1));
                    r.put("suggest_label", rs.getString(2));
                }
                return null;
            }, r.get("warehouse_id"), cls, "B3".equals(cls) ? "AMBIENT" : cls);
        }
        return rows;
    }

    record PutawayRequest(Long itemId, Long lotId, Long fromLocationId, Long toLocationId, BigDecimal qty) {
    }

    @PostMapping("/putaway")
    @Transactional
    public Map<String, Object> putaway(@RequestBody PutawayRequest r) {
        perm.require("SCM-22", Action.CREATE);
        if (r.toLocationId() == null || r.qty() == null || r.qty().signum() <= 0) {
            throw new BusinessException("PUT", "Pilih lokasi tujuan dan qty");
        }
        String lotNo = r.lotId() == null ? "-" : jdbc.queryForObject("SELECT lot_no FROM scm.lot WHERE id = ?", String.class, r.lotId());
        Long id = inventory.move(new InventoryService.MoveCommand(InventoryService.MoveType.TRANSFER, r.itemId(), r.lotId(), r.fromLocationId(),
                r.toLocationId(), r.qty(), null, time.today(), "PUT", r.lotId() == null ? 0L : r.lotId(), lotNo, "Putaway dari karantina"));
        return Map.of("moveId", id);
    }

    // ------------------------------------------------------------------ SCM-24 Terima barang jadi

    @GetMapping("/fg-receipts")
    public List<Map<String, Object>> fgReceipts(@RequestParam(defaultValue = "false") boolean pendingOnly) {
        perm.require("SCM-24", Action.VIEW);
        return jdbc.queryForList("""
                SELECT o.id, o.doc_no, o.doc_date, o.qty_good, o.received_qty, o.received_at, o.receive_note, u.full_name AS received_by_name,
                       w.doc_no AS wo_no, w.batch_no, i.code AS item_code, i.name AS item_name, l.qc_status, l.exp_date,
                       wh.code || '/' || loc.bin_code AS location
                FROM pre.production_output o JOIN pre.work_order w ON w.id = o.wo_id JOIN sys.item i ON i.id = w.product_item_id
                LEFT JOIN scm.lot l ON l.id = o.lot_id JOIN sys.location loc ON loc.id = o.to_location_id
                JOIN sys.warehouse wh ON wh.id = loc.warehouse_id LEFT JOIN sys.app_user u ON u.id = o.received_by
                WHERE o.plant_id = ? AND o.status = 'POSTED' AND (NOT ? OR o.received_at IS NULL)
                ORDER BY o.received_at NULLS FIRST, o.doc_date DESC LIMIT 500""", UserContext.plantId(), pendingOnly);
    }

    // ------------------------------------------------------------------ SCM-44 Kedaluwarsa & slow moving

    @GetMapping("/expiry")
    public Map<String, Object> expiry(@RequestParam(defaultValue = "6") int months, @RequestParam(defaultValue = "90") int idleDays) {
        perm.require("SCM-44", Action.VIEW);
        Long plant = UserContext.plantId();
        LocalDate today = time.today();
        String base = """
                SELECT l.id, l.lot_no, l.exp_date, l.qc_status, i.code AS item_code, i.name AS item_name, u.code AS uom,
                       SUM(q.qty) AS qty, SUM(q.qty * q.unit_cost) AS value,
                       (SELECT MAX(m.move_date) FROM scm.stock_move m WHERE m.lot_id = l.id) AS last_move
                FROM scm.lot l JOIN scm.stock_quant q ON q.lot_id = l.id AND q.qty > 0 JOIN sys.item i ON i.id = l.item_id
                JOIN sys.uom u ON u.id = i.uom_id JOIN sys.location loc ON loc.id = q.location_id
                JOIN sys.warehouse w ON w.id = loc.warehouse_id WHERE w.plant_id = ? %s
                GROUP BY l.id, i.code, i.name, u.code""";
        List<Map<String, Object>> expiring = jdbc.queryForList(base.formatted("AND l.exp_date <= ?") + " ORDER BY l.exp_date", plant,
                Date.valueOf(today.plusMonths(months)));
        List<Map<String, Object>> slow = jdbc.queryForList("SELECT * FROM (" + base.formatted("") + ") x WHERE last_move < ? ORDER BY last_move",
                plant, Date.valueOf(today.minusDays(idleDays)));
        return Map.of("expiring", expiring, "slowMoving", slow);
    }

    // ------------------------------------------------------------------ SCM-45 Penelusuran lot

    /** Mundur (bahan → supplier) dan maju (batch → customer) dari satu lot, maks. 4 tingkat. */
    @GetMapping("/trace")
    public Map<String, Object> trace(@RequestParam Long lotId) {
        perm.require("SCM-45", Action.VIEW);
        Map<String, Object> lot = jdbc.queryForList("""
                SELECT l.id, l.lot_no, l.supplier_lot, l.qc_status, l.mfg_date, l.exp_date, l.source_doc, i.code AS item_code, i.name AS item_name
                FROM scm.lot l JOIN sys.item i ON i.id = l.item_id WHERE l.id = ?""", lotId).stream().findFirst()
                .orElseThrow(() -> new BusinessException("LOT", "Lot tidak ditemukan"));
        List<Map<String, Object>> back = new ArrayList<>();
        backward(lotId, 0, back, new HashSet<>());
        List<Map<String, Object>> fwd = new ArrayList<>();
        forward(lotId, 0, fwd, new HashSet<>());
        return Map.of("lot", lot, "backward", back, "forward", fwd);
    }

    private void backward(Long lotId, int depth, List<Map<String, Object>> out, Set<Long> seen) {
        if (depth > 4 || !seen.add(lotId)) {
            return;
        }
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT 'GR' AS kind, g.id AS doc_id, g.doc_no, g.doc_date, p.name AS party, po.doc_no AS ref, gl.qty, gl.supplier_lot, gl.manufacturer
                FROM scm.gr_line gl JOIN scm.gr g ON g.id = gl.gr_id JOIN sys.partner p ON p.id = g.partner_id JOIN prc.po po ON po.id = g.po_id
                WHERE gl.lot_id = ?""", lotId)) {
            r.put("depth", depth);
            out.add(r);
        }
        for (Map<String, Object> wo : jdbc.queryForList("""
                SELECT 'WO' AS kind, w.id AS doc_id, w.doc_no, o.doc_date, l.code || ' · ' || l.name AS party, w.batch_no AS ref, o.qty_good AS qty
                FROM pre.production_output o JOIN pre.work_order w ON w.id = o.wo_id JOIN pre.line l ON l.id = w.line_id
                WHERE o.lot_id = ? AND o.status = 'POSTED'""", lotId)) {
            wo.put("depth", depth);
            out.add(wo);
            for (Map<String, Object> m : jdbc.queryForList("""
                    SELECT 'MATERIAL' AS kind, m.lot_id, l.lot_no AS ref, i.code || ' · ' || i.name AS party, SUM(m.qty) AS qty, MIN(m.move_date) AS doc_date,
                           r.doc_no
                    FROM scm.stock_move m JOIN pre.material_request r ON m.ref_doc_type = 'MR' AND m.ref_doc_id = r.id
                    JOIN scm.lot l ON l.id = m.lot_id JOIN sys.item i ON i.id = m.item_id
                    WHERE r.wo_id = ? AND m.lot_id IS NOT NULL GROUP BY m.lot_id, l.lot_no, i.code, i.name, r.doc_no""", wo.get("doc_id"))) {
                m.put("depth", depth + 1);
                out.add(m);
                backward(((Number) m.get("lot_id")).longValue(), depth + 2, out, seen);
            }
        }
    }

    private void forward(Long lotId, int depth, List<Map<String, Object>> out, Set<Long> seen) {
        if (depth > 4 || !seen.add(lotId)) {
            return;
        }
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT 'DO' AS kind, d.id AS doc_id, d.doc_no, d.doc_date, p.name AS party, s.doc_no AS ref, SUM(dl.qty) AS qty
                FROM scm.delivery_line dl JOIN scm.delivery d ON d.id = dl.delivery_id JOIN sys.partner p ON p.id = d.partner_id
                JOIN scm.so s ON s.id = d.so_id WHERE dl.lot_id = ? AND d.status IN ('POSTED','DONE')
                GROUP BY d.id, d.doc_no, d.doc_date, p.name, s.doc_no""", lotId)) {
            r.put("depth", depth);
            out.add(r);
        }
        for (Map<String, Object> wo : jdbc.queryForList("""
                SELECT 'WO' AS kind, w.id AS doc_id, w.doc_no, MIN(m.move_date) AS doc_date, w.batch_no AS ref, SUM(m.qty) AS qty,
                       (SELECT o.lot_id FROM pre.production_output o WHERE o.wo_id = w.id AND o.status = 'POSTED' LIMIT 1) AS out_lot
                FROM scm.stock_move m JOIN pre.material_request r ON m.ref_doc_type = 'MR' AND m.ref_doc_id = r.id
                JOIN pre.work_order w ON w.id = r.wo_id WHERE m.lot_id = ? GROUP BY w.id, w.doc_no, w.batch_no""", lotId)) {
            wo.put("depth", depth);
            wo.put("party", "Dipakai di batch " + wo.get("ref"));
            out.add(wo);
            if (wo.get("out_lot") != null) {
                forward(((Number) wo.get("out_lot")).longValue(), depth + 1, out, seen);
            }
        }
    }

    // ------------------------------------------------------------------ SCM-90 Laporan supply chain

    @GetMapping("/report")
    public Map<String, Object> report(@RequestParam int year, @RequestParam int month) {
        perm.require("SCM-90", Action.VIEW);
        Long plant = UserContext.plantId();
        YearMonth ym = YearMonth.of(year, month);
        String period = "%d%02d".formatted(year, month);
        List<Map<String, Object>> accuracy = jdbc.queryForList("""
                SELECT i.code, i.name, COALESCE(f.qty, 0) AS forecast, COALESCE(a.qty, 0) AS actual
                FROM sys.item i LEFT JOIN scm.forecast f ON f.item_id = i.id AND f.plant_id = ? AND f.period = ?
                LEFT JOIN (SELECT dl.item_id, SUM(dl.qty) AS qty FROM scm.delivery_line dl JOIN scm.delivery d ON d.id = dl.delivery_id
                           WHERE d.plant_id = ? AND d.status IN ('POSTED','DONE') AND d.doc_date BETWEEN ? AND ? GROUP BY dl.item_id) a ON a.item_id = i.id
                WHERE i.type = 'FG' AND i.active ORDER BY i.code""", plant, period, plant, Date.valueOf(ym.atDay(1)), Date.valueOf(ym.atEndOfMonth()));
        for (Map<String, Object> r : accuracy) {
            BigDecimal f = (BigDecimal) r.get("forecast");
            BigDecimal a = (BigDecimal) r.get("actual");
            r.put("accuracy_pct", f.signum() == 0 ? null : BigDecimal.ONE.subtract(a.subtract(f).abs().divide(f, 4, RoundingMode.HALF_UP))
                    .max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP));
        }
        List<Map<String, Object>> aging = jdbc.queryForList("""
                SELECT CASE WHEN age <= 30 THEN '0–30 hari' WHEN age <= 90 THEN '31–90 hari' WHEN age <= 180 THEN '91–180 hari' ELSE '> 180 hari' END AS bucket,
                       SUM(value) AS value, COUNT(*) AS lots
                FROM (SELECT current_date - (l.created_at AT TIME ZONE 'Asia/Jakarta')::date AS age, q.qty * q.unit_cost AS value
                      FROM scm.stock_quant q JOIN scm.lot l ON l.id = q.lot_id JOIN sys.location loc ON loc.id = q.location_id
                      JOIN sys.warehouse w ON w.id = loc.warehouse_id WHERE q.qty > 0 AND w.plant_id = ?) x
                GROUP BY 1 ORDER BY MIN(age)""", plant);
        Map<String, BigDecimal> kpi = ScmKpis.compute(jdbc, ym, plant);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("forecastAccuracy", accuracy);
        out.put("aging", aging);
        out.put("otifPct", kpi.get("SCM_OTIF"));
        out.put("stockAccuracyPct", kpi.get("SCM_ACC"));
        return out;
    }
}

/** KPI SCM untuk SARMUT & dashboard. */
@Component
class ScmKpis implements KpiProvider, DashboardProvider, LookupSource {

    private final JdbcTemplate jdbc;

    ScmKpis(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static Map<String, BigDecimal> compute(JdbcTemplate jdbc, YearMonth period, Long plantId) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        Map<String, Object> otif = jdbc.queryForMap("""
                SELECT COUNT(*) AS lines,
                       COUNT(*) FILTER (WHERE (SELECT COALESCE(SUM(dl.qty), 0) FROM scm.delivery_line dl JOIN scm.delivery d ON d.id = dl.delivery_id
                                               WHERE dl.so_line_id = l.id AND d.status IN ('POSTED','DONE') AND d.doc_date <= l.delivery_date) >= l.qty) AS ok
                FROM scm.so_line l JOIN scm.so s ON s.id = l.so_id
                WHERE s.plant_id = ? AND s.status IN ('APPROVED','DONE') AND l.delivery_date BETWEEN ? AND ?""",
                plantId, Date.valueOf(period.atDay(1)), Date.valueOf(period.atEndOfMonth()));
        long lines = ((Number) otif.get("lines")).longValue();
        if (lines > 0) {
            out.put("SCM_OTIF", BigDecimal.valueOf(((Number) otif.get("ok")).longValue() * 100.0 / lines).setScale(2, RoundingMode.HALF_UP));
        }
        Map<String, Object> acc = jdbc.queryForMap("""
                SELECT COALESCE(SUM(lines_total), 0) AS total, COALESCE(SUM(lines_accurate), 0) AS ok FROM scm.stock_count
                WHERE plant_id = ? AND status IN ('POSTED','DONE') AND doc_date BETWEEN ? AND ?""",
                plantId, Date.valueOf(period.atDay(1)), Date.valueOf(period.atEndOfMonth()));
        long total = ((Number) acc.get("total")).longValue();
        if (total > 0) {
            out.put("SCM_ACC", BigDecimal.valueOf(((Number) acc.get("ok")).longValue() * 100.0 / total).setScale(2, RoundingMode.HALF_UP));
        }
        return out;
    }

    @Override
    public Map<String, BigDecimal> compute(YearMonth period, Long plantId) {
        return compute(jdbc, period, plantId);
    }

    @Override
    public String appCode() {
        return "SCM";
    }

    @Override
    public List<Kpi> kpis(Long plantId) {
        BigDecimal value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(q.qty * q.unit_cost), 0) FROM scm.stock_quant q JOIN sys.location loc ON loc.id = q.location_id
                JOIN sys.warehouse w ON w.id = loc.warehouse_id WHERE w.plant_id = ?""", BigDecimal.class, plantId);
        Long quarantine = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT l.id) FROM scm.lot l JOIN scm.stock_quant q ON q.lot_id = l.id AND q.qty > 0
                JOIN sys.location loc ON loc.id = q.location_id JOIN sys.warehouse w ON w.id = loc.warehouse_id
                WHERE w.plant_id = ? AND l.qc_status = 'QUARANTINE'""", Long.class, plantId);
        Map<String, Object> so = jdbc.queryForMap("""
                SELECT COUNT(*) AS n, COALESCE(SUM(total), 0) AS v FROM scm.so WHERE plant_id = ? AND status = 'APPROVED'""", plantId);
        Long expiring = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT l.id) FROM scm.lot l JOIN scm.stock_quant q ON q.lot_id = l.id AND q.qty > 0
                JOIN sys.location loc ON loc.id = q.location_id JOIN sys.warehouse w ON w.id = loc.warehouse_id
                WHERE w.plant_id = ? AND l.exp_date <= current_date + 180""", Long.class, plantId);
        BigDecimal otif = compute(jdbc, YearMonth.now(), plantId).get("SCM_OTIF");
        return List.of(
                new Kpi("Nilai persediaan", "Rp " + String.format("%,.0f", value).replace(',', '.'), "semua gudang plant", "SCM-40"),
                new Kpi("Lot di karantina", String.valueOf(quarantine), "menunggu keputusan QA", "SCM-21"),
                new Kpi("Pesanan terbuka", String.valueOf(so.get("n")), "Rp " + String.format("%,.0f", (BigDecimal) so.get("v")).replace(',', '.'), "SCM-02"),
                new Kpi("OTIF bulan ini", otif == null ? "–" : otif.stripTrailingZeros().toPlainString() + "%",
                        expiring + " lot kedaluwarsa < 6 bulan", "SCM-08"));
    }

    @Override
    public Map<String, Def> lookups() {
        return Map.of(
                "bins", new Def("""
                        (SELECT l.id, w.code || '/' || l.bin_code AS label, w.name || COALESCE(' · ' || l.zone, '') AS wname, l.temp_class,
                                l.warehouse_id, l.is_quarantine, w.plant_id, l.active AND w.active AS ok FROM sys.location l
                         JOIN sys.warehouse w ON w.id = l.warehouse_id) x""", "label", "wname", "temp_class", "ok",
                        Set.of("warehouse_id", "is_quarantine", "plant_id")),
                "stock-lots", new Def("""
                        (SELECT l.id, l.lot_no, COALESCE(l.supplier_lot, '') || CASE WHEN l.exp_date IS NULL THEN '' ELSE ' · ED ' || to_char(l.exp_date, 'DD/MM/YY') END AS info,
                                l.qc_status, l.item_id, (SELECT SUM(qty) FROM scm.stock_quant q WHERE q.lot_id = l.id) AS stock FROM scm.lot l) x""",
                        "lot_no", "info", "qc_status", "stock > 0", Set.of("item_id", "qc_status")),
                "so-approved", new Def("""
                        (SELECT s.id, s.doc_no, p.name, s.customer_po, s.partner_id, s.status, s.plant_id FROM scm.so s JOIN sys.partner p ON p.id = s.partner_id) x""",
                        "doc_no", "name", "customer_po", "status = 'APPROVED'", Set.of("partner_id", "plant_id")),
                "deliveries-posted", new Def("""
                        (SELECT d.id, d.doc_no, p.name, to_char(d.doc_date, 'DD/MM/YYYY') AS tgl, d.partner_id, d.status, d.plant_id
                         FROM scm.delivery d JOIN sys.partner p ON p.id = d.partner_id) x""",
                        "doc_no", "name", "tgl", "status = 'POSTED'", Set.of("partner_id", "plant_id")),
                "fg-items", new Def("sys.item", "code", "name", "type", "active AND type IN ('FG','WIP')", Set.of("type")),
                "so-lines-open", new Def("""
                        (SELECT l.id, i.code, i.name, (l.qty - l.qty_shipped)::text AS sisa, l.qty - l.qty_shipped AS sisa_num, l.so_id, l.closed
                         FROM scm.so_line l JOIN sys.item i ON i.id = l.item_id) x""", "code", "name", "sisa", "NOT closed AND sisa_num > 0", Set.of("so_id")),
                "delivery-lines", new Def("""
                        (SELECT dl.id, COALESCE(l.lot_no, i.code) AS code, i.name, dl.qty::text AS qty, dl.delivery_id FROM scm.delivery_line dl
                         JOIN sys.item i ON i.id = dl.item_id LEFT JOIN scm.lot l ON l.id = dl.lot_id) x""", "code", "name", "qty", null,
                        Set.of("delivery_id")));
    }
}
