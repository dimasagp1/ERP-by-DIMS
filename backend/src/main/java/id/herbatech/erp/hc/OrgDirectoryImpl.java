package id.herbatech.erp.hc;

import id.herbatech.erp.shared.approval.OrgDirectory;
import id.herbatech.erp.shared.security.ViewScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Struktur organisasi HC-02 untuk kernel: atasan langsung (dasar approval) dan cakupan data per peran.
 * Perubahan posisi/atasan langsung otomatis berlaku untuk approval berikutnya.
 */
@Component
class OrgDirectoryImpl implements OrgDirectory {

    private static final int MAX_DEPTH = 10;

    private final JdbcTemplate jdbc;

    OrgDirectoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Long superiorUserId(Long userId, LocalDate onDate) {
        Long positionId = jdbc.query("""
                        SELECT e.position_id FROM sys.app_user u JOIN hc.employee e ON e.id = u.employee_id
                        WHERE u.id = ?""", rs -> rs.next() ? (Long) rs.getObject(1) : null, userId);
        for (int depth = 0; positionId != null && depth < MAX_DEPTH; depth++) {
            Long parent = jdbc.query("SELECT reports_to_id FROM hc.position WHERE id = ?",
                    rs -> rs.next() ? (Long) rs.getObject(1) : null, positionId);
            if (parent == null) {
                return null;
            }
            List<Map<String, Object>> holders = jdbc.queryForList("""
                    SELECT u.id AS user_id, e.delegate_id, e.delegate_until
                    FROM hc.employee e JOIN sys.app_user u ON u.employee_id = e.id AND u.active
                    WHERE e.position_id = ? AND e.status = 'ACTIVE' AND e.active
                    ORDER BY e.id LIMIT 1""", parent);
            if (!holders.isEmpty()) {
                Map<String, Object> h = holders.getFirst();
                Object until = h.get("delegate_until");
                if (h.get("delegate_id") != null && until != null && !((Date) until).toLocalDate().isBefore(onDate)) {
                    Long delegateUser = jdbc.query(
                            "SELECT id FROM sys.app_user WHERE employee_id = ? AND active ORDER BY id LIMIT 1",
                            rs -> rs.next() ? rs.getLong(1) : null, ((Number) h.get("delegate_id")).longValue());
                    if (delegateUser != null && !delegateUser.equals(userId)) {
                        return delegateUser;
                    }
                }
                return ((Number) h.get("user_id")).longValue();
            }
            positionId = parent; // posisi atasan kosong: naik satu tingkat lagi
        }
        return null;
    }

    @Override
    public Set<Long> visibleCreatorIds(Long userId, ViewScope scope) {
        if (scope == ViewScope.ALL) {
            return null;
        }
        if (scope == ViewScope.OWN) {
            return Set.of(userId);
        }
        Long deptId = jdbc.query("""
                        SELECT e.department_id FROM sys.app_user u JOIN hc.employee e ON e.id = u.employee_id WHERE u.id = ?""",
                rs -> rs.next() ? (Long) rs.getObject(1) : null, userId);
        if (deptId == null) {
            return Set.of(userId);
        }
        // SECTION = departemen/seksi sendiri; DEPARTMENT = departemen teratas beserta semua seksinya.
        Long root = deptId;
        if (scope == ViewScope.DEPARTMENT) {
            for (int depth = 0; depth < MAX_DEPTH; depth++) {
                Long parent = jdbc.query("SELECT parent_id FROM sys.department WHERE id = ?",
                        rs -> rs.next() ? (Long) rs.getObject(1) : null, root);
                if (parent == null) {
                    break;
                }
                root = parent;
            }
        }
        Set<Long> ids = new HashSet<>(jdbc.queryForList("""
                WITH RECURSIVE d AS (
                    SELECT id FROM sys.department WHERE id = ?
                    UNION ALL SELECT c.id FROM sys.department c JOIN d ON c.parent_id = d.id
                )
                SELECT u.id FROM sys.app_user u JOIN hc.employee e ON e.id = u.employee_id
                WHERE e.department_id IN (SELECT id FROM d)""", Long.class, root));
        ids.add(userId);
        return ids;
    }
}
