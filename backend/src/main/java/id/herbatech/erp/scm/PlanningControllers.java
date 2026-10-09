package id.herbatech.erp.scm;

import id.herbatech.erp.shared.config.TimeService;
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

/** PPIC: forecast & S&OP (SCM-03), MPS (SCM-04), MRP (SCM-05), kapasitas lini (SCM-06), rencana vs aktual (SCM-08). */
@RestController
@RequestMapping("/api/scm")
class PlanningControllers {

    private final JdbcTemplate jdbc;
    private final PermissionService perm;
    private final MrpService mrp;
    private final TimeService time;

    PlanningControllers(JdbcTemplate jdbc, PermissionService perm, MrpService mrp, TimeService time) {
        this.jdbc = jdbc;
        this.perm = perm;
        this.mrp = mrp;
        this.time = time;
    }

    // ------------------------------------------------------------------ SCM-03 Forecast & S&OP

    record ForecastCell(Long itemId, String period, BigDecimal qty) {
    }

    /** Forecast 12 bulan per produk jadi + realisasi kirim untuk akurasi forecast. */
    @GetMapping("/forecast")
    public Map<String, Object> forecast(@RequestParam int year) {
        perm.require("SCM-03", Action.VIEW);
        Long plant = UserContext.plantId();
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT i.id, i.code, i.name, u.code AS uom FROM sys.item i JOIN sys.uom u ON u.id = i.uom_id
                WHERE i.type = 'FG' AND i.active ORDER BY i.code""");
        List<Map<String, Object>> cells = jdbc.queryForList("""
                SELECT item_id, period, qty FROM scm.forecast WHERE plant_id = ? AND period LIKE ?""", plant, year + "%");
        List<Map<String, Object>> actual = jdbc.queryForList("""
                SELECT dl.item_id, to_char(d.doc_date, 'YYYYMM') AS period, SUM(dl.qty) AS qty
                FROM scm.delivery_line dl JOIN scm.delivery d ON d.id = dl.delivery_id
                WHERE d.plant_id = ? AND d.status IN ('POSTED','DONE') AND EXTRACT(YEAR FROM d.doc_date) = ?
                GROUP BY dl.item_id, 2""", plant, year);
        List<Map<String, Object>> notes = jdbc.queryForList("SELECT period, meeting_on, decisions FROM scm.sop_note WHERE plant_id = ? AND period LIKE ?",
                plant, year + "%");
        return Map.of("items", items, "forecast", cells, "actual", actual, "notes", notes);
    }

    @PutMapping("/forecast")
    @Transactional
    public Map<String, Object> saveForecast(@RequestBody List<ForecastCell> cells) {
        perm.require("SCM-03", Action.EDIT);
        Long plant = UserContext.plantId();
        for (ForecastCell c : cells) {
            jdbc.update("""
                    INSERT INTO scm.forecast (plant_id, item_id, period, qty, updated_by) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (plant_id, item_id, period) DO UPDATE SET qty = EXCLUDED.qty, updated_at = now(), updated_by = EXCLUDED.updated_by""",
                    plant, c.itemId(), c.period(), c.qty() == null ? BigDecimal.ZERO : c.qty().max(BigDecimal.ZERO), UserContext.userId());
        }
        return Map.of("saved", cells.size());
    }

    record SopNote(String period, LocalDate meetingOn, String decisions) {
    }

    @PutMapping("/sop-notes")
    @Transactional
    public Map<String, Object> saveNote(@RequestBody SopNote n) {
        perm.require("SCM-03", Action.EDIT);
        jdbc.update("""
                INSERT INTO scm.sop_note (plant_id, period, meeting_on, decisions, updated_by) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (plant_id, period) DO UPDATE SET meeting_on = EXCLUDED.meeting_on, decisions = EXCLUDED.decisions,
                    updated_at = now(), updated_by = EXCLUDED.updated_by""",
                UserContext.plantId(), n.period(), n.meetingOn() == null ? null : Date.valueOf(n.meetingOn()), n.decisions(), UserContext.userId());
        return Map.of("ok", true);
    }

    // ------------------------------------------------------------------ SCM-04 MPS

    record MpsCell(Long itemId, LocalDate weekStart, BigDecimal qty, Boolean firm) {
    }

    /** MPS per produk per minggu, dengan usulan: kebutuhan (maks forecast mingguan, pesanan) vs stok proyeksi & stok pengaman. */
    @GetMapping("/mps")
    public Map<String, Object> mps(@RequestParam(defaultValue = "12") int weeks) {
        perm.require("SCM-04", Action.VIEW);
        Long plant = UserContext.plantId();
        LocalDate from = MrpService.monday(time.today());
        List<LocalDate> cols = new ArrayList<>();
        for (int w = 0; w < weeks; w++) {
            cols.add(from.plusWeeks(w));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : jdbc.queryForList("""
                SELECT i.id, i.code, i.name, u.code AS uom, COALESCE(sp.safety_stock, 0) AS ss, COALESCE(b.base_qty, 0) AS batch
                FROM sys.item i JOIN sys.uom u ON u.id = i.uom_id
                LEFT JOIN scm.stock_param sp ON sp.item_id = i.id AND sp.plant_id = ?
                LEFT JOIN LATERAL (SELECT base_qty FROM scm.bom WHERE item_id = i.id AND plant_id = ? AND status = 'POSTED' ORDER BY revision DESC LIMIT 1) b ON TRUE
                WHERE i.type = 'FG' AND i.active ORDER BY i.code""", plant, plant)) {
            Long itemId = ((Number) item.get("id")).longValue();
            BigDecimal projected = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(q.qty), 0) FROM scm.stock_quant q JOIN sys.location loc ON loc.id = q.location_id
                    JOIN sys.warehouse w ON w.id = loc.warehouse_id LEFT JOIN scm.lot l ON l.id = q.lot_id
                    WHERE q.item_id = ? AND w.plant_id = ? AND (l.id IS NULL OR l.qc_status IN ('RELEASED','QUARANTINE'))""",
                    BigDecimal.class, itemId, plant);
            projected = projected.add(jdbc.queryForObject("""
                    SELECT COALESCE(SUM(qty_plan), 0) FROM pre.work_order WHERE product_item_id = ? AND plant_id = ? AND status IN ('SUBMITTED','APPROVED')""",
                    BigDecimal.class, itemId, plant));
            BigDecimal ss = (BigDecimal) item.get("ss");
            BigDecimal batch = (BigDecimal) item.get("batch");
            List<Map<String, Object>> cells = new ArrayList<>();
            for (LocalDate wk : cols) {
                YearMonth ym = YearMonth.from(wk);
                BigDecimal fc = jdbc.queryForList("SELECT qty FROM scm.forecast WHERE plant_id = ? AND item_id = ? AND period = ?", BigDecimal.class,
                        plant, itemId, "%d%02d".formatted(ym.getYear(), ym.getMonthValue())).stream().findFirst().orElse(BigDecimal.ZERO);
                BigDecimal fcWeek = fc.multiply(BigDecimal.valueOf(7)).divide(BigDecimal.valueOf(ym.lengthOfMonth()), 0, RoundingMode.HALF_UP);
                BigDecimal so = jdbc.queryForObject("""
                        SELECT COALESCE(SUM(l.qty - l.qty_shipped), 0) FROM scm.so_line l JOIN scm.so s ON s.id = l.so_id
                        WHERE l.item_id = ? AND s.plant_id = ? AND s.status = 'APPROVED' AND NOT l.closed
                          AND l.delivery_date >= ? AND l.delivery_date < ?""", BigDecimal.class, itemId, plant, Date.valueOf(wk),
                        Date.valueOf(wk.plusWeeks(1)));
                BigDecimal demand = fcWeek.max(so);
                Map<String, Object> saved = jdbc.queryForList("SELECT qty, firm FROM scm.mps WHERE plant_id = ? AND item_id = ? AND week_start = ?",
                        plant, itemId, Date.valueOf(wk)).stream().findFirst().orElse(null);
                BigDecimal plan = saved == null ? BigDecimal.ZERO : (BigDecimal) saved.get("qty");
                projected = projected.subtract(demand);
                BigDecimal suggest = BigDecimal.ZERO;
                if (projected.add(plan).compareTo(ss) < 0) {
                    suggest = MrpService.roundUp(ss.subtract(projected.add(plan)), BigDecimal.ZERO, batch);
                }
                projected = projected.add(plan);
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("weekStart", wk);
                c.put("forecast", fcWeek);
                c.put("orders", so);
                c.put("qty", plan);
                c.put("firm", saved != null && (Boolean) saved.get("firm"));
                c.put("suggest", suggest);
                c.put("projected", projected);
                cells.add(c);
            }
            Map<String, Object> r = new LinkedHashMap<>(item);
            r.put("cells", cells);
            rows.add(r);
        }
        return Map.of("weeks", cols, "rows", rows);
    }

    @PutMapping("/mps")
    @Transactional
    public Map<String, Object> saveMps(@RequestBody List<MpsCell> cells) {
        perm.require("SCM-04", Action.EDIT);
        Long plant = UserContext.plantId();
        for (MpsCell c : cells) {
            LocalDate wk = MrpService.monday(c.weekStart());
            jdbc.update("""
                    INSERT INTO scm.mps (plant_id, item_id, week_start, qty, firm, updated_by) VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (plant_id, item_id, week_start) DO UPDATE SET qty = EXCLUDED.qty, firm = EXCLUDED.firm,
                        updated_at = now(), updated_by = EXCLUDED.updated_by""",
                    plant, c.itemId(), Date.valueOf(wk), c.qty() == null ? BigDecimal.ZERO : c.qty().max(BigDecimal.ZERO),
                    Boolean.TRUE.equals(c.firm()), UserContext.userId());
        }
        return Map.of("saved", cells.size());
    }

    // ------------------------------------------------------------------ SCM-05 MRP

    @GetMapping("/mrp/latest")
    public Map<String, Object> latest() {
        perm.require("SCM-05", Action.VIEW);
        Long plant = UserContext.plantId();
        Map<String, Object> run = jdbc.queryForList("""
                SELECT r.*, u.full_name AS triggered_by_name FROM scm.mrp_run r LEFT JOIN sys.app_user u ON u.id = r.triggered_by
                WHERE r.plant_id = ? ORDER BY r.id DESC LIMIT 1""", plant).stream().findFirst().orElse(null);
        if (run == null) {
            return Map.of();
        }
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT m.*, i.code AS item_code, i.name AS item_name, i.type AS item_type, u.code AS uom, p.name AS partner_name,
                       pr.doc_no AS pr_no
                FROM scm.mrp_line m JOIN sys.item i ON i.id = m.item_id JOIN sys.uom u ON u.id = i.uom_id
                LEFT JOIN sys.partner p ON p.id = m.partner_id
                LEFT JOIN prc.pr_line pl ON pl.id = m.pr_line_id LEFT JOIN prc.pr pr ON pr.id = pl.pr_id
                WHERE m.run_id = ? ORDER BY m.action DESC, m.order_date, i.code""", run.get("id"));
        return Map.of("run", run, "lines", lines);
    }

    @PostMapping("/mrp/run")
    public Map<String, Object> runMrp() {
        perm.require("SCM-05", Action.CREATE);
        return Map.of("runId", mrp.run(UserContext.plantId(), "MANUAL", UserContext.userId()));
    }

    // ------------------------------------------------------------------ SCM-06 Kapasitas lini

    /** Beban per lini per minggu dari MPS (lini default produk) & WO terbuka, dibanding kapasitas per shift (3 shift × 6 hari). */
    @GetMapping("/capacity")
    public Map<String, Object> capacity(@RequestParam(defaultValue = "8") int weeks) {
        perm.require("SCM-06", Action.VIEW);
        Long plant = UserContext.plantId();
        LocalDate from = MrpService.monday(time.today());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> line : jdbc.queryForList("""
                SELECT id, code, name, capacity_per_shift, capacity_uom FROM pre.line WHERE plant_id = ? AND active ORDER BY code""", plant)) {
            Long lineId = ((Number) line.get("id")).longValue();
            BigDecimal cap = line.get("capacity_per_shift") == null ? BigDecimal.ZERO : (BigDecimal) line.get("capacity_per_shift");
            List<Map<String, Object>> cells = new ArrayList<>();
            for (int w = 0; w < weeks; w++) {
                LocalDate wk = from.plusWeeks(w);
                BigDecimal mps = jdbc.queryForObject("""
                        SELECT COALESCE(SUM(m.qty), 0) FROM scm.mps m LEFT JOIN pre.product_param pp ON pp.item_id = m.item_id
                        WHERE m.plant_id = ? AND m.week_start = ? AND COALESCE(m.line_id, pp.default_line_id) = ?""",
                        BigDecimal.class, plant, Date.valueOf(wk), lineId);
                BigDecimal wo = jdbc.queryForObject("""
                        SELECT COALESCE(SUM(qty_plan), 0) FROM pre.work_order WHERE line_id = ? AND status IN ('DRAFT','SUBMITTED','APPROVED')
                          AND planned_start >= ? AND planned_start < ?""", BigDecimal.class, lineId, Date.valueOf(wk), Date.valueOf(wk.plusWeeks(1)));
                BigDecimal load = mps.max(wo);
                BigDecimal shifts = cap.signum() == 0 ? BigDecimal.ZERO : load.divide(cap, 1, RoundingMode.CEILING);
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("weekStart", wk);
                c.put("mps", mps);
                c.put("wo", wo);
                c.put("load", load);
                c.put("shifts", shifts);
                c.put("pct", shifts.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(18), 0, RoundingMode.HALF_UP));
                cells.add(c);
            }
            Map<String, Object> r = new LinkedHashMap<>(line);
            r.put("cells", cells);
            rows.add(r);
        }
        return Map.of("rows", rows, "shiftsPerWeek", 18, "note", "Kapasitas = kapasitas per shift × 3 shift × 6 hari kerja; beban = maks(MPS, WO terbuka).");
    }

    // ------------------------------------------------------------------ SCM-08 Rencana vs aktual

    @GetMapping("/plan-actual")
    public Map<String, Object> planActual(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        perm.require("SCM-08", Action.VIEW);
        Long plant = UserContext.plantId();
        List<Map<String, Object>> wos = jdbc.queryForList("""
                SELECT w.id, w.doc_no, w.batch_no, w.status, w.planned_start, w.planned_end, w.qty_plan, w.qty_good, w.yield_pct,
                       w.finished_at, i.code AS item_code, i.name AS item_name, l.code AS line_code,
                       CASE WHEN w.finished_at IS NOT NULL THEN (w.finished_at AT TIME ZONE 'Asia/Jakarta')::date > w.planned_end
                            ELSE w.planned_end < current_date END AS late
                FROM pre.work_order w JOIN sys.item i ON i.id = w.product_item_id JOIN pre.line l ON l.id = w.line_id
                WHERE w.plant_id = ? AND w.planned_start BETWEEN ? AND ? AND w.status <> 'CANCELLED' ORDER BY w.planned_start""",
                plant, Date.valueOf(from), Date.valueOf(to));
        List<Map<String, Object>> orders = jdbc.queryForList("""
                SELECT s.doc_no, p.name AS customer, i.code AS item_code, l.qty, l.delivery_date,
                       COALESCE((SELECT SUM(dl.qty) FROM scm.delivery_line dl JOIN scm.delivery d ON d.id = dl.delivery_id
                                 WHERE dl.so_line_id = l.id AND d.status IN ('POSTED','DONE') AND d.doc_date <= l.delivery_date), 0) AS on_time,
                       l.qty_shipped
                FROM scm.so_line l JOIN scm.so s ON s.id = l.so_id JOIN sys.partner p ON p.id = s.partner_id JOIN sys.item i ON i.id = l.item_id
                WHERE s.plant_id = ? AND l.delivery_date BETWEEN ? AND ? AND s.status IN ('APPROVED','DONE') ORDER BY l.delivery_date""",
                plant, Date.valueOf(from), Date.valueOf(to));
        long otif = orders.stream().filter(o -> ((BigDecimal) o.get("on_time")).compareTo((BigDecimal) o.get("qty")) >= 0).count();
        long lateWo = wos.stream().filter(w -> Boolean.TRUE.equals(w.get("late"))).count();
        return Map.of("workOrders", wos, "orders", orders, "otifPct", orders.isEmpty() ? null : otif * 100.0 / orders.size(),
                "lateWo", lateWo);
    }
}
