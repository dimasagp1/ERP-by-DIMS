package id.herbatech.erp.pre;

import id.herbatech.erp.hc.HcQueries;
import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.meta.DashboardProvider;
import id.herbatech.erp.shared.meta.KpiProvider;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.web.LookupSource;
import id.herbatech.erp.shared.web.MasterController;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Master PRE-99, jam kerja lini PRE-12, dashboard & KPI produksi. */
final class PreControllers {

    private PreControllers() {
    }

    @RestController
    @RequestMapping("/api/pre/lines")
    static class LineController extends MasterController<ProductionLine> {
        LineController(ProductionLineRepository r, PermissionService p) { super(r, p, ProductionLine.class); }
        @Override protected String menuCode() { return "PRE-02"; }
        @Override protected String[] immutableFields() { return new String[]{"code"}; }

        @Override
        protected void beforeSave(ProductionLine l, ProductionLine existing) {
            l.setProcessCode(l.getProcessCode().trim().toUpperCase());
        }
    }

    @RestController
    @RequestMapping("/api/pre/product-params")
    static class ProductParamController extends MasterController<ProductParam> {
        ProductParamController(ProductParamRepository r, PermissionService p) { super(r, p, ProductParam.class); }
        @Override protected String menuCode() { return "PRE-02"; }
        @Override protected List<String> searchFields() { return List.of("batchPrefix"); }
        @Override protected Sort defaultSort() { return Sort.by("batchPrefix"); }
    }

    @RestController
    @RequestMapping("/api/pre/reject-reasons")
    static class RejectReasonController extends MasterController<RejectReason> {
        RejectReasonController(RejectReasonRepository r, PermissionService p) { super(r, p, RejectReason.class); }
        @Override protected String menuCode() { return "PRE-08"; }
        @Override protected String[] immutableFields() { return new String[]{"code"}; }
    }

    /** PRE-12 Jam kerja lini: total jam per karyawan per hari tidak boleh melebihi jam hadir di absensi (HC-07). */
    @RestController
    @RequestMapping("/api/pre/labor")
    static class LaborController extends MasterController<LaborEntry> {

        private final HcQueries hc;
        private final JdbcTemplate jdbc;
        private final TimeService time;

        LaborController(LaborEntryRepository r, PermissionService p, HcQueries hc, JdbcTemplate jdbc, TimeService time) {
            super(r, p, LaborEntry.class);
            this.hc = hc;
            this.jdbc = jdbc;
            this.time = time;
        }

        @Override protected String menuCode() { return "PRE-12"; }
        @Override protected List<String> searchFields() { return List.of("activity"); }
        @Override protected Sort defaultSort() { return Sort.by("workDate").descending(); }

        @Override
        protected void beforeSave(LaborEntry e, LaborEntry existing) {
            String status = jdbc.queryForList("SELECT status FROM pre.work_order WHERE id = ?", String.class, e.getWoId())
                    .stream().findFirst().orElseThrow(() -> new BusinessException("LBR_WO", "Work order tidak ditemukan"));
            if (!List.of("APPROVED", "DONE").contains(status)) {
                throw new BusinessException("LBR_WO", "Jam kerja hanya untuk WO yang sudah dirilis");
            }
            BigDecimal present = hc.workHours(e.getEmployeeId(), e.getWorkDate());
            if (present == null) {
                throw new BusinessException("LBR_ATT", hc.employeeName(e.getEmployeeId()) + " tidak tercatat HADIR pada " + e.getWorkDate()
                        + " di absensi (HC-07)");
            }
            BigDecimal other = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(hours), 0) FROM pre.labor_entry WHERE employee_id = ? AND work_date = ? AND active AND id <> ?""",
                    BigDecimal.class, e.getEmployeeId(), Date.valueOf(e.getWorkDate()), existing == null ? -1L : existing.getId());
            if (other.add(e.getHours()).compareTo(present) > 0) {
                throw BusinessException.of("LBR_HOURS", "Total jam %s melebihi jam hadir %s jam", other.add(e.getHours()).toPlainString(),
                        present.toPlainString());
            }
        }

        /** Usulkan jam kerja dari absensi untuk operator WO (hari ini atau tanggal tertentu). */
        @PostMapping("/from-attendance")
        @Transactional
        public Map<String, Object> fromAttendance(@RequestParam Long woId, @RequestParam(required = false) LocalDate date) {
            perm.require("PRE-12", Action.CREATE);
            LocalDate d = date == null ? time.today() : date;
            int created = 0;
            for (Map<String, Object> r : jdbc.queryForList("""
                    SELECT o.employee_id, a.work_hours - COALESCE((SELECT SUM(le.hours) FROM pre.labor_entry le
                          WHERE le.employee_id = o.employee_id AND le.work_date = ? AND le.active), 0) AS free
                    FROM pre.wo_operator o JOIN hc.attendance a ON a.employee_id = o.employee_id AND a.work_date = ? AND a.status = 'HADIR'
                    WHERE o.wo_id = ?""", Date.valueOf(d), Date.valueOf(d), woId)) {
                BigDecimal free = (BigDecimal) r.get("free");
                if (free != null && free.signum() > 0) {
                    jdbc.update("""
                            INSERT INTO pre.labor_entry (wo_id, employee_id, work_date, hours, activity, created_by)
                            VALUES (?, ?, ?, ?, 'Dari absensi', ?)""", woId, r.get("employee_id"), Date.valueOf(d), free.min(BigDecimal.valueOf(16)),
                            id.herbatech.erp.shared.security.UserContext.userId());
                    created++;
                }
            }
            return Map.of("created", created);
        }
    }

    @Component
    static class PreDashboard implements DashboardProvider, KpiProvider, LookupSource {

        private final JdbcTemplate jdbc;
        private final TimeService time;

        PreDashboard(JdbcTemplate jdbc, TimeService time) {
            this.jdbc = jdbc;
            this.time = time;
        }

        @Override
        public String appCode() {
            return "PRE";
        }

        @Override
        public List<Kpi> kpis(Long plantId) {
            LocalDate first = time.today().withDayOfMonth(1);
            Map<String, Object> m = monthStats(plantId, first, time.today());
            Long running = jdbc.queryForObject("""
                    SELECT count(*) FROM pre.work_order WHERE plant_id = ? AND status = 'APPROVED' AND started_at IS NOT NULL""", Long.class, plantId);
            Long released = jdbc.queryForObject("""
                    SELECT count(*) FROM pre.work_order WHERE plant_id = ? AND status = 'APPROVED' AND started_at IS NULL""", Long.class, plantId);
            BigDecimal ach = (BigDecimal) m.get("achievement");
            BigDecimal yield = (BigDecimal) m.get("yield");
            return List.of(
                    new Kpi("Pencapaian output bulan ini", ach == null ? "–" : pct(ach), m.get("batches") + " batch selesai · PRE-08", "PRE-08"),
                    new Kpi("Yield rata-rata", yield == null ? "–" : pct(yield), "Batch selesai bulan ini", "PRE-08"),
                    new Kpi("WO berjalan", String.valueOf(running), "Sudah dimulai di lini", "PRE-02"),
                    new Kpi("WO menunggu mulai", String.valueOf(released), "Sudah dirilis", "PRE-02"));
        }

        private Map<String, Object> monthStats(Long plantId, LocalDate from, LocalDate to) {
            return jdbc.queryForMap("""
                    SELECT count(*) AS batches,
                           CASE WHEN SUM(qty_plan) > 0 THEN SUM(qty_good) * 100 / SUM(qty_plan) END AS achievement,
                           AVG(yield_pct) AS yield
                    FROM pre.work_order WHERE plant_id = ? AND status = 'DONE'
                      AND (finished_at AT TIME ZONE 'Asia/Jakarta')::date BETWEEN ? AND ?""", plantId, Date.valueOf(from), Date.valueOf(to));
        }

        private static String pct(BigDecimal v) {
            return v.setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%";
        }

        @Override
        public Map<String, BigDecimal> compute(YearMonth period, Long plantId) {
            Map<String, Object> m = monthStats(plantId, period.atDay(1), period.atEndOfMonth());
            Map<String, BigDecimal> out = new HashMap<>();
            if (m.get("achievement") != null) {
                out.put("PRE_OUTPUT", (BigDecimal) m.get("achievement"));
            }
            if (m.get("yield") != null) {
                out.put("PRE_YIELD", (BigDecimal) m.get("yield"));
            }
            return out;
        }

        @Override
        public Map<String, Def> lookups() {
            return Map.of(
                    "lines", new Def("pre.line", "code", "name", "process_code", "active", Set.of("plant_id")),
                    "reject-reasons", Def.of("pre.reject_reason", "code", "name"),
                    "work-orders", new Def("pre.work_order", "doc_no", "COALESCE(batch_no, '(belum rilis)')", "status",
                            "status IN ('APPROVED','DONE')", Set.of("status", "line_id")));
        }
    }
}
