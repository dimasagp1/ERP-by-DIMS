package id.herbatech.erp.sys;

import id.herbatech.erp.shared.approval.ApprovalRule;
import id.herbatech.erp.shared.approval.ApprovalRuleRepository;
import id.herbatech.erp.shared.document.DocType;
import id.herbatech.erp.shared.document.DocTypeRepository;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.meta.DashboardProvider;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.Role;
import id.herbatech.erp.shared.security.RoleRepository;
import id.herbatech.erp.shared.security.UserContext;
import id.herbatech.erp.shared.web.LookupSource;
import id.herbatech.erp.shared.web.PageResponse;
import org.springframework.http.ResponseEntity;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** SYS-13 log integrasi, pengaturan per aplikasi (gear), lookup & dashboard SYS. */
final class SysSupportControllers {

    private SysSupportControllers() {
    }

    // ---------------------------------------------------------------- SYS-13 Integrasi & log API

    @RestController
    @RequestMapping("/api/sys/integration-logs")
    static class IntegrationLogController {

        record LogRow(Long id, String system, String direction, String endpoint, String refDoc, String status,
                      Integer httpStatus, String error, int attempts, Instant createdAt, Instant lastAttemptAt) {
        }

        private final JdbcTemplate jdbc;
        private final PermissionService perm;

        IntegrationLogController(JdbcTemplate jdbc, PermissionService perm) {
            this.jdbc = jdbc;
            this.perm = perm;
        }

        @GetMapping
        public PageResponse<LogRow> list(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String system,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
            perm.require("SYS-13", Action.VIEW);
            StringBuilder where = new StringBuilder(" WHERE 1=1");
            List<Object> args = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                where.append(" AND status = ?");
                args.add(status);
            }
            if (system != null && !system.isBlank()) {
                where.append(" AND system = ?");
                args.add(system);
            }
            Long total = jdbc.queryForObject("SELECT count(*) FROM sys.integration_log" + where, Long.class, args.toArray());
            List<Object> pageArgs = new ArrayList<>(args);
            pageArgs.add(Math.min(size, 500));
            pageArgs.add((long) page * size);
            List<LogRow> rows = jdbc.query("SELECT * FROM sys.integration_log" + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                    (rs, i) -> new LogRow(rs.getLong("id"), rs.getString("system"), rs.getString("direction"),
                            rs.getString("endpoint"), rs.getString("ref_doc"), rs.getString("status"),
                            (Integer) rs.getObject("http_status"), rs.getString("error"), rs.getInt("attempts"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("last_attempt_at") == null ? null : rs.getTimestamp("last_attempt_at").toInstant()),
                    pageArgs.toArray());
            return PageResponse.of(rows, page, size, total == null ? 0 : total);
        }

        /** Menandai ulang untuk dikirim; pengirim integrasi mengambil status PENDING. */
        @PostMapping("/{id}/retry")
        @Transactional
        public ResponseEntity<Void> retry(@PathVariable Long id) {
            perm.require("SYS-13", Action.EDIT);
            int n = jdbc.update("UPDATE sys.integration_log SET status = 'PENDING' WHERE id = ? AND status = 'FAILED'", id);
            if (n == 0) {
                throw new BusinessException("RETRY", "Hanya log berstatus FAILED yang bisa dicoba ulang");
            }
            return ResponseEntity.noContent().build();
        }
    }

    // ---------------------------------------------------------------- Pengaturan aplikasi (gear, PRD §15.4)

    @RestController
    @RequestMapping("/api/settings/{app}")
    static class AppSettingsController {

        /** Kunci yang boleh disimpan per aplikasi beserta nilai bawaannya. */
        static final Map<String, String> DEFAULTS = Map.of(
                "attachmentRequiredOnSubmit", "true",
                "reasonRequiredOnCancel", "true",
                "allowNextDayDate", "true",
                "defaultPlant", "",
                "displayName", "");

        /** Kunci khusus Pengaturan Sistem: endpoint integrasi (SYS-13). Token tidak pernah dikirim balik utuh. */
        static final Map<String, String> SYS_DEFAULTS = Map.of(
                "integration.bsc.url", "",
                "integration.bsc.token", "",
                "integration.odoo.url", "",
                "integration.odoo.token", "");

        record AccessRow(String role, String roleName, String viewScope, List<String> actions) {
        }

        record AppSettings(Map<String, String> values, List<DocType> docTypes, List<ApprovalRule> approvalRules,
                           List<AccessRow> access, boolean canEdit) {
        }

        private final JdbcTemplate jdbc;
        private final PermissionService perm;
        private final DocTypeRepository docTypes;
        private final ApprovalRuleRepository rules;
        private final RoleRepository roles;

        AppSettingsController(JdbcTemplate jdbc, PermissionService perm, DocTypeRepository docTypes,
                              ApprovalRuleRepository rules, RoleRepository roles) {
            this.jdbc = jdbc;
            this.perm = perm;
            this.docTypes = docTypes;
            this.rules = rules;
            this.roles = roles;
        }

        @GetMapping
        public AppSettings get(@PathVariable String app) {
            perm.require(app + "-01", Action.VIEW);
            Map<String, String> values = new LinkedHashMap<>(DEFAULTS);
            if ("SYS".equals(app)) {
                values.putAll(SYS_DEFAULTS);
            }
            jdbc.query("SELECT key, value FROM sys.app_setting WHERE app_code = ?",
                    rs -> {
                        String k = rs.getString("key");
                        String v = rs.getString("value");
                        values.put(k, k.endsWith(".token") && v != null && !v.isBlank() ? "********" : v);
                    }, app);
            List<DocType> types = docTypes.findByAppCodeOrderByCode(app);
            List<ApprovalRule> appRules = types.isEmpty() ? List.of()
                    : rules.findByDocTypeCodeInOrderByDocTypeCodeAscLevelAsc(types.stream().map(DocType::getCode).toList());
            List<AccessRow> access = roles.findAll().stream().filter(Role::isActive)
                    .map(r -> new AccessRow(r.getCode(), r.getName(), r.getViewScope().name(),
                            r.actionSet().stream().map(Enum::name).toList()))
                    .toList();
            return new AppSettings(values, types, appRules, access, perm.canManageSettings(app));
        }

        @PutMapping
        @Transactional
        public AppSettings put(@PathVariable String app, @RequestBody Map<String, String> values) {
            perm.requireSettings(app);
            values.forEach((k, v) -> {
                if (k.endsWith(".token") && "********".equals(v)) {
                    return; // token tidak diubah
                }
                if (!DEFAULTS.containsKey(k) && !("SYS".equals(app) && SYS_DEFAULTS.containsKey(k))) {
                    throw new BusinessException("SETTING", "Pengaturan tidak dikenal: " + k);
                }
                jdbc.update("""
                        INSERT INTO sys.app_setting (app_code, key, value, created_by) VALUES (?, ?, ?, ?)
                        ON CONFLICT (app_code, key) DO UPDATE SET value = EXCLUDED.value, updated_at = now(), updated_by = EXCLUDED.created_by""",
                        app, k, v, UserContext.userId());
            });
            return get(app);
        }
    }

    // ---------------------------------------------------------------- Lookup & dashboard SYS

    @Component
    static class SysLookups implements LookupSource {
        @Override
        public Map<String, Def> lookups() {
            Map<String, Def> m = new LinkedHashMap<>();
            m.put("companies", Def.of("sys.company", "code", "name"));
            m.put("plants", Def.of("sys.plant", "code", "name"));
            m.put("departments", new Def("sys.department", "code", "name", "app_code", "active", Set.of("parent_id", "app_code")));
            m.put("cost-centers", new Def("sys.cost_center", "code", "name", null, "active", Set.of("department_id", "plant_id", "production")));
            m.put("uoms", new Def("sys.uom", "code", "name", "category", "active", Set.of("category")));
            m.put("items", new Def("sys.item", "code", "name", "type", "active AND status <> 'OBSOLETE'", Set.of("type", "status", "lot_tracked")));
            m.put("partners", new Def("sys.partner", "code", "name", "type", "active", Set.of("type")));
            m.put("warehouses", new Def("sys.warehouse", "code", "name", "type", "active", Set.of("plant_id", "type")));
            m.put("locations", new Def("sys.location", "bin_code", "zone", "temp_class", "active", Set.of("warehouse_id", "is_quarantine")));
            m.put("shifts", Def.of("sys.shift", "code", "name"));
            m.put("currencies", Def.of("sys.currency", "code", "name"));
            m.put("tax-codes", new Def("sys.tax_code", "code", "name", "type", "active", Set.of("type")));
            m.put("roles", Def.of("sys.role", "code", "name"));
            m.put("users", new Def("sys.app_user", "username", "full_name", null, "active", Set.of()));
            m.put("doc-types", new Def("sys.doc_type", "code", "name", "app_code", "active", Set.of("app_code")));
            return m;
        }
    }

    @Component
    static class SysDashboard implements DashboardProvider {

        private final JdbcTemplate jdbc;

        SysDashboard(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public String appCode() {
            return "SYS";
        }

        @Override
        public List<Kpi> kpis(Long plantId) {
            return List.of(
                    new Kpi("Pengguna aktif", count("SELECT count(*) FROM sys.app_user WHERE active"), "SYS-03", "SYS-03"),
                    new Kpi("Integrasi gagal, 24 jam",
                            count("SELECT count(*) FROM sys.integration_log WHERE status = 'FAILED' AND created_at > now() - interval '24 hours'"),
                            "SYS-13", "SYS-13"),
                    new Kpi("Aturan approval aktif", count("SELECT count(*) FROM sys.approval_rule WHERE active"), "SYS-04", "SYS-04"),
                    new Kpi("Perubahan data minggu ini",
                            count("SELECT count(DISTINCT (table_name, record_id, ts)) FROM core.audit_log WHERE ts > now() - interval '7 days'"),
                            "SYS-14", "SYS-14"));
        }

        private String count(String sql) {
            Long n = jdbc.queryForObject(sql, Long.class);
            return String.format("%,d", n == null ? 0 : n).replace(',', '.');
        }
    }
}
