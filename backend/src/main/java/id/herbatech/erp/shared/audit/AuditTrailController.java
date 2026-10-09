package id.herbatech.erp.shared.audit;

import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.web.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** SYS-14 Audit Trail Global: baca saja untuk Auditor, QA, dan Admin. */
@RestController
@RequestMapping("/api/audit")
public class AuditTrailController {

    public record AuditRow(Long id, String tableName, String recordId, String action, String field, String oldValue,
                           String newValue, String reason, Long userId, String username, Instant ts) {
    }

    private final JdbcTemplate jdbc;
    private final PermissionService perm;

    public AuditTrailController(JdbcTemplate jdbc, PermissionService perm) {
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @GetMapping
    public PageResponse<AuditRow> search(@RequestParam(required = false) String table,
                                         @RequestParam(required = false) String recordId,
                                         @RequestParam(required = false) String username,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        if (!perm.has("SYS-14", Action.AUDIT) && !perm.has("SYS-14", Action.VIEW)) {
            perm.require("SYS-14", Action.AUDIT);
        }
        size = Math.min(Math.max(size, 1), 500);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (table != null && !table.isBlank()) {
            where.append(" AND table_name = ?");
            args.add(table);
        }
        if (recordId != null && !recordId.isBlank()) {
            where.append(" AND record_id = ?");
            args.add(recordId);
        }
        if (username != null && !username.isBlank()) {
            where.append(" AND username ILIKE ?");
            args.add("%" + username + "%");
        }
        ZoneId wib = ZoneId.of("Asia/Jakarta");
        if (from != null) {
            where.append(" AND ts >= ?");
            args.add(Timestamp.from(from.atStartOfDay(wib).toInstant()));
        }
        if (to != null) {
            where.append(" AND ts < ?");
            args.add(Timestamp.from(to.plusDays(1).atStartOfDay(wib).toInstant()));
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM core.audit_log" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<AuditRow> rows = jdbc.query("SELECT * FROM core.audit_log" + where + " ORDER BY ts DESC, id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new AuditRow(rs.getLong("id"), rs.getString("table_name"), rs.getString("record_id"),
                        rs.getString("action"), rs.getString("field"), rs.getString("old_value"), rs.getString("new_value"),
                        rs.getString("reason"), (Long) rs.getObject("user_id"), rs.getString("username"),
                        rs.getTimestamp("ts").toInstant()),
                pageArgs.toArray());
        return PageResponse.of(rows, page, size, total == null ? 0 : total);
    }

    @GetMapping("/tables")
    public List<String> tables() {
        perm.require("SYS-14", Action.VIEW);
        return jdbc.queryForList("SELECT DISTINCT table_name FROM core.audit_log ORDER BY 1", String.class);
    }
}
