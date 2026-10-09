package id.herbatech.erp.shared.audit;

import id.herbatech.erp.shared.domain.SkipAudit;
import id.herbatech.erp.shared.security.CurrentUser;
import id.herbatech.erp.shared.security.UserContext;
import jakarta.persistence.Table;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Audit trail global (SYS-14): setiap insert/update/delete entitas JPA dicatat per field dengan nilai lama,
 * nilai baru, alasan, pengguna, dan waktu server. Ditulis dalam transaksi yang sama dengan perubahannya,
 * ke tabel append-only.
 */
@Component
public class AuditEventListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private static final Set<String> IGNORED = Set.of("version", "createdAt", "createdBy", "updatedAt", "updatedBy",
            "lastLoginAt", "failedAttempts", "lockedUntil");
    private static final Set<String> MASKED = Set.of("passwordHash", "pinHash");
    private static final int MAX_VALUE = 2000;

    private final JdbcTemplate jdbc;
    private final Map<Class<?>, String> tableNames = new ConcurrentHashMap<>();

    public AuditEventListener(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        Class<?> type = event.getEntity().getClass();
        if (skip(type)) {
            return;
        }
        String[] names = event.getPersister().getPropertyNames();
        Object[] state = event.getState();
        StringJoiner summary = new StringJoiner("; ");
        for (int i = 0; i < names.length; i++) {
            if (!IGNORED.contains(names[i]) && state[i] != null && !(state[i] instanceof Collection<?>)) {
                summary.add(names[i] + "=" + format(names[i], state[i]));
            }
        }
        write(java.util.Collections.singletonList(row(type, event.getId(), "INSERT", null, null, truncate(summary.toString()))));
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        Class<?> type = event.getEntity().getClass();
        if (skip(type) || event.getOldState() == null) {
            return;
        }
        String[] names = event.getPersister().getPropertyNames();
        Object[] oldState = event.getOldState();
        Object[] state = event.getState();
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            if (IGNORED.contains(names[i]) || state[i] instanceof Collection<?>) {
                continue;
            }
            if (!same(oldState[i], state[i])) {
                rows.add(row(type, event.getId(), "UPDATE", names[i], format(names[i], oldState[i]), format(names[i], state[i])));
            }
        }
        write(rows);
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        Class<?> type = event.getEntity().getClass();
        if (skip(type)) {
            return;
        }
        write(java.util.Collections.singletonList(row(type, event.getId(), "DELETE", null, null, null)));
    }

    private Object[] row(Class<?> type, Object id, String action, String field, String oldValue, String newValue) {
        CurrentUser u = UserContext.currentOptional().orElse(null);
        return new Object[]{tableName(type), String.valueOf(id), action, field, oldValue, newValue, AuditContext.reason(),
                u == null ? null : u.id(), u == null ? "system" : u.username()};
    }

    private void write(List<Object[]> rows) {
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO core.audit_log (table_name, record_id, action, field, old_value, new_value, reason, user_id, username)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", rows);
        }
    }

    private String tableName(Class<?> type) {
        return tableNames.computeIfAbsent(type, t -> {
            Table table = t.getAnnotation(Table.class);
            if (table == null) {
                return t.getSimpleName();
            }
            return table.schema().isEmpty() ? table.name() : table.schema() + "." + table.name();
        });
    }

    private static boolean skip(Class<?> type) {
        return type.isAnnotationPresent(SkipAudit.class);
    }

    private static boolean same(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }

    private static String format(String name, Object value) {
        if (value == null) {
            return null;
        }
        if (MASKED.contains(name)) {
            return "********";
        }
        return truncate(value instanceof BigDecimal bd ? bd.toPlainString() : String.valueOf(value));
    }

    private static String truncate(String s) {
        return s == null || s.length() <= MAX_VALUE ? s : s.substring(0, MAX_VALUE) + "…";
    }
}
