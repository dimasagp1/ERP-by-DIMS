package id.herbatech.erp.hc;

import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.meta.DashboardProvider;
import id.herbatech.erp.shared.meta.KpiProvider;
import id.herbatech.erp.shared.notification.NotificationService;
import id.herbatech.erp.shared.period.PeriodLockService;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import id.herbatech.erp.shared.security.UserDirectory;
import id.herbatech.erp.shared.web.LookupSource;
import id.herbatech.erp.shared.web.MasterController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Master HC-99, absensi HC-07, kualifikasi HC-12, kontrak HC-06, dashboard & KPI HC. */
final class HcControllers {

    private HcControllers() {
    }

    // ---------------------------------------------------------------- master HC-99 / HC-10 / HC-12 / HC-06 / HC-13

    @RestController
    @RequestMapping("/api/hc/leave-types")
    static class LeaveTypeController extends MasterController<LeaveType> {
        LeaveTypeController(LeaveTypeRepository r, PermissionService p) { super(r, p, LeaveType.class); }
        @Override protected String menuCode() { return "HC-08"; }
        @Override protected String[] immutableFields() { return new String[]{"code"}; }
    }

    @RestController
    @RequestMapping("/api/hc/leave-entitlements")
    static class LeaveEntitlementController extends MasterController<LeaveEntitlement> {
        LeaveEntitlementController(LeaveEntitlementRepository r, PermissionService p) { super(r, p, LeaveEntitlement.class); }
        @Override protected String menuCode() { return "HC-08"; }
        @Override protected List<String> searchFields() { return List.of(); }
        @Override protected Sort defaultSort() { return Sort.by("year").descending(); }
    }

    @RestController
    @RequestMapping("/api/hc/salary-components")
    static class SalaryComponentController extends MasterController<SalaryComponent> {
        SalaryComponentController(SalaryComponentRepository r, PermissionService p) { super(r, p, SalaryComponent.class); }
        @Override protected String menuCode() { return "HC-09"; }
        @Override protected Sort defaultSort() { return Sort.by("seq"); }
        @Override protected String[] immutableFields() { return new String[]{"code", "system"}; }

        @Override
        protected void beforeSave(SalaryComponent c, SalaryComponent existing) {
            if (existing == null) {
                c.setSystem(false);
            }
        }
    }

    @RestController
    @RequestMapping("/api/hc/payroll-params")
    static class PayrollParamController extends MasterController<PayrollParam> {
        PayrollParamController(PayrollParamRepository r, PermissionService p) { super(r, p, PayrollParam.class); }
        @Override protected String menuCode() { return "HC-10"; }
        @Override protected List<String> searchFields() { return List.of("key", "description"); }
        @Override protected Sort defaultSort() { return Sort.by("key"); }
        @Override protected String[] immutableFields() { return new String[]{"key"}; }

        @Override
        protected void beforeSave(PayrollParam p, PayrollParam existing) {
            if (!"DEDUCT_ALPA".equals(p.getKey())) {
                try {
                    new BigDecimal(p.getValue());
                } catch (NumberFormatException e) {
                    throw new BusinessException("PARAM", "Nilai parameter " + p.getKey() + " harus angka (gunakan titik untuk desimal)");
                }
            }
        }
    }

    @RestController
    @RequestMapping("/api/hc/ptkp")
    static class PtkpController extends MasterController<Ptkp> {
        PtkpController(PtkpRepository r, PermissionService p) { super(r, p, Ptkp.class); }
        @Override protected String menuCode() { return "HC-09"; }
        @Override protected List<String> searchFields() { return List.of("status"); }
        @Override protected Sort defaultSort() { return Sort.by("status"); }
    }

    @RestController
    @RequestMapping("/api/hc/pph21-ter")
    static class TerController extends MasterController<Pph21Ter> {
        TerController(Pph21TerRepository r, PermissionService p) { super(r, p, Pph21Ter.class); }
        @Override protected String menuCode() { return "HC-09"; }
        @Override protected List<String> searchFields() { return List.of("category"); }
        @Override protected Sort defaultSort() { return Sort.by("category", "upperLimit"); }
    }

    @RestController
    @RequestMapping("/api/hc/qualifications")
    static class QualificationController extends MasterController<Qualification> {
        QualificationController(QualificationRepository r, PermissionService p) { super(r, p, Qualification.class); }
        @Override protected String menuCode() { return "HC-12"; }
        @Override protected List<String> searchFields() { return List.of("processCode", "note"); }
        @Override protected Sort defaultSort() { return Sort.by("validUntil"); }

        @Override
        protected void beforeSave(Qualification q, Qualification existing) {
            q.setProcessCode(q.getProcessCode().trim().toUpperCase());
            if (q.getValidUntil().isBefore(q.getValidFrom())) {
                throw new BusinessException("QUAL_DATE", "Berlaku sampai tidak boleh sebelum berlaku mulai");
            }
        }
    }

    @RestController
    @RequestMapping("/api/hc/contracts")
    static class ContractController extends MasterController<EmploymentContract> {
        ContractController(EmploymentContractRepository r, PermissionService p) { super(r, p, EmploymentContract.class); }
        @Override protected String menuCode() { return "HC-06"; }
        @Override protected List<String> searchFields() { return List.of("contractNo", "note"); }
        @Override protected Sort defaultSort() { return Sort.by("endDate"); }
        @Override protected String[] immutableFields() { return new String[]{"contractNo"}; }

        @Override
        protected void beforeSave(EmploymentContract c, EmploymentContract existing) {
            if ("PKWT".equals(c.getType()) && c.getEndDate() == null) {
                throw new BusinessException("CONTRACT", "Kontrak PKWT wajib punya tanggal berakhir");
            }
            if (c.getEndDate() != null && c.getEndDate().isBefore(c.getStartDate())) {
                throw new BusinessException("CONTRACT", "Tanggal berakhir tidak boleh sebelum tanggal mulai");
            }
        }
    }

    @RestController
    @RequestMapping("/api/hc/sarmut-kpis")
    static class SarmutKpiController extends MasterController<SarmutKpi> {
        SarmutKpiController(SarmutKpiRepository r, PermissionService p) { super(r, p, SarmutKpi.class); }
        @Override protected String menuCode() { return "HC-13"; }
        @Override protected Sort defaultSort() { return Sort.by("appCode", "code"); }
        @Override protected String[] immutableFields() { return new String[]{"code"}; }
    }

    // ---------------------------------------------------------------- HC-07 Absensi

    @RestController
    @RequestMapping("/api/hc/attendance")
    static class AttendanceController {

        record Entry(@NotNull Long employeeId, @NotNull LocalDate workDate, Long shiftId, LocalTime checkIn, LocalTime checkOut,
                     @NotNull String status, String note) {
        }

        record Lock(int year, int month, boolean locked) {
        }

        private final AttendanceService attendance;
        private final PeriodLockService periods;
        private final PermissionService perm;

        AttendanceController(AttendanceService attendance, PeriodLockService periods, PermissionService perm) {
            this.attendance = attendance;
            this.periods = periods;
            this.perm = perm;
        }

        @GetMapping
        public Map<String, Object> list(@RequestParam String period, @RequestParam(required = false) Long employeeId,
                                        @RequestParam(required = false) Long departmentId) {
            perm.require("HC-07", Action.VIEW);
            YearMonth ym = PayrollService.period(period);
            return Map.of("rows", attendance.list(ym, employeeId, departmentId, UserContext.plantId()),
                    "locked", attendance.isLocked(ym.atDay(1)),
                    "workDays", attendance.workingDays(ym.atDay(1), ym.atEndOfMonth()).size());
        }

        @PutMapping
        public Map<String, Object> upsert(@Valid @RequestBody Entry e) {
            perm.require("HC-07", Action.EDIT);
            attendance.upsert(e.employeeId(), e.workDate(), e.shiftId(), e.checkIn(), e.checkOut(), e.status(), e.note());
            return Map.of("ok", true);
        }

        @PostMapping("/import")
        public AttendanceService.ImportResult importCsv(@RequestPart("file") MultipartFile file) throws IOException {
            perm.require("HC-07", Action.CREATE);
            return attendance.importCsv(file.getInputStream(), file.getOriginalFilename());
        }

        /** Kunci absensi periode = prasyarat payroll; membuka kembali hanya Manager HC. */
        @PostMapping("/lock")
        public Map<String, Object> lock(@RequestBody Lock req) {
            perm.require("HC-07", Action.POST);
            periods.setLocked("HC", req.year(), req.month(), req.locked(), UserContext.userId());
            return Map.of("locked", req.locked());
        }
    }
}

/** API untuk modul Produksi: operator hanya boleh ditugaskan bila kualifikasinya aktif (PRE aturan 1, HC aturan 2). */
@Service
class QualificationCheck {

    private final JdbcTemplate jdbc;

    QualificationCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    boolean isQualified(Long employeeId, String processCode, LocalDate onDate) {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM hc.qualification WHERE employee_id = ? AND process_code = ? AND active
                  AND valid_from <= ? AND valid_until >= ?""", Long.class, employeeId, processCode, Date.valueOf(onDate), Date.valueOf(onDate));
        return n != null && n > 0;
    }
}

@Component
class HcDashboard implements DashboardProvider, KpiProvider, LookupSource {

    private final JdbcTemplate jdbc;
    private final TimeService time;
    private final NotificationService notifications;
    private final UserDirectory users;

    HcDashboard(JdbcTemplate jdbc, TimeService time, NotificationService notifications, UserDirectory users) {
        this.jdbc = jdbc;
        this.time = time;
        this.notifications = notifications;
        this.users = users;
    }

    @Override
    public String appCode() {
        return "HC";
    }

    @Override
    public List<Kpi> kpis(Long plantId) {
        LocalDate today = time.today();
        Long headcount = jdbc.queryForObject("SELECT count(*) FROM hc.employee WHERE status = 'ACTIVE' AND plant_id = ?", Long.class, plantId);
        Map<String, Object> att = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE a.status = 'HADIR') AS hadir, count(*) AS total FROM hc.attendance a
                JOIN hc.employee e ON e.id = a.employee_id WHERE a.work_date = ? AND e.plant_id = ?""", Date.valueOf(today), plantId);
        BigDecimal otHours = jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.hours), 0) FROM hc.overtime_line l JOIN hc.overtime_request r ON r.id = l.request_id
                WHERE r.plant_id = ? AND r.status IN ('APPROVED','POSTED','DONE') AND r.work_date >= ?""",
                BigDecimal.class, plantId, Date.valueOf(today.withDayOfMonth(1)));
        Long contracts = jdbc.queryForObject("""
                SELECT count(*) FROM hc.employment_contract c JOIN hc.employee e ON e.id = c.employee_id
                WHERE c.active AND c.end_date BETWEEN ? AND ? AND e.status = 'ACTIVE' AND e.plant_id = ?""",
                Long.class, Date.valueOf(today), Date.valueOf(today.plusDays(30)), plantId);
        long total = ((Number) att.get("total")).longValue();
        String rate = total == 0 ? "–" : BigDecimal.valueOf(((Number) att.get("hadir")).longValue() * 100.0 / total)
                .setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%";
        return List.of(
                new Kpi("Karyawan aktif", String.valueOf(headcount), "HC-03", "HC-03"),
                new Kpi("Kehadiran hari ini", rate, total == 0 ? "Belum ada absensi tercatat" : total + " tercatat", "HC-07"),
                new Kpi("Lembur disetujui bulan ini", otHours.stripTrailingZeros().toPlainString() + " jam", "HC-08", "HC-08"),
                new Kpi("Kontrak habis ≤ 30 hari", String.valueOf(contracts), "HC-06", "HC-06"));
    }

    /** HC_ATTENDANCE: hadir / (hadir + alpa + izin + sakit); HC_TURNOVER: keluar / rata-rata karyawan. */
    @Override
    public Map<String, BigDecimal> compute(YearMonth period, Long plantId) {
        Map<String, BigDecimal> out = new HashMap<>();
        Date from = Date.valueOf(period.atDay(1));
        Date to = Date.valueOf(period.atEndOfMonth());
        Map<String, Object> a = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE a.status = 'HADIR') AS hadir,
                       count(*) FILTER (WHERE a.status IN ('HADIR','ALPA','IZIN','SAKIT')) AS basis
                FROM hc.attendance a JOIN hc.employee e ON e.id = a.employee_id
                WHERE a.work_date BETWEEN ? AND ? AND e.plant_id = ?""", from, to, plantId);
        long basis = ((Number) a.get("basis")).longValue();
        if (basis > 0) {
            out.put("HC_ATTENDANCE", BigDecimal.valueOf(((Number) a.get("hadir")).longValue() * 100.0 / basis));
        }
        Map<String, Object> t = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE end_date BETWEEN ? AND ?) AS leavers,
                       count(*) FILTER (WHERE join_date <= ? AND (end_date IS NULL OR end_date >= ?)) AS avg_hc
                FROM hc.employee WHERE plant_id = ?""", from, to, to, from, plantId);
        long hc = ((Number) t.get("avg_hc")).longValue();
        if (hc > 0) {
            out.put("HC_TURNOVER", BigDecimal.valueOf(((Number) t.get("leavers")).longValue() * 100.0 / hc));
        }
        return out;
    }

    @Override
    public Map<String, Def> lookups() {
        return Map.of(
                "leave-types", Def.of("hc.leave_type", "code", "name"),
                "salary-components", new Def("hc.salary_component", "code", "name", "kind", "active", Set.of("kind", "system")),
                "sarmut-kpis", new Def("hc.sarmut_kpi", "code", "name", "app_code", "active", Set.of("source", "app_code")));
    }

    /** Setiap hari kerja 07:15 WIB: ingatkan Manager HC kontrak yang berakhir 30 hari lagi (HC-06). */
    @Scheduled(cron = "0 15 7 * * MON-FRI", zone = "Asia/Jakarta")
    @Transactional
    public void remindContracts() {
        LocalDate target = time.today().plusDays(30);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.contract_no, e.name, e.plant_id FROM hc.employment_contract c JOIN hc.employee e ON e.id = c.employee_id
                WHERE c.active AND c.end_date = ? AND e.status = 'ACTIVE'""", Date.valueOf(target));
        for (Map<String, Object> r : rows) {
            notifications.notify(users.usersWithRole("MANAGER", "HC", ((Number) r.get("plant_id")).longValue()), "CONTRACT",
                    "Kontrak berakhir 30 hari lagi: " + r.get("name"), "Kontrak " + r.get("contract_no") + " berakhir " + target,
                    "/app/HC/m/HC-06");
        }
    }
}
