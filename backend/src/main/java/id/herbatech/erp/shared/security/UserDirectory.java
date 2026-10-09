package id.herbatech.erp.shared.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Pencarian pengguna untuk approval & notifikasi (siapa pemegang peran X di aplikasi Y). */
@Component
public class UserDirectory {

    private final JdbcTemplate jdbc;

    public UserDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Pengguna aktif yang memegang peran di aplikasi (atau '*') dan plant (atau semua plant). */
    public List<Long> usersWithRole(String roleCode, String appCode, Long plantId) {
        return jdbc.queryForList("""
                SELECT DISTINCT u.id FROM sys.app_user u
                JOIN sys.user_role ur ON ur.user_id = u.id
                JOIN sys.role r ON r.id = ur.role_id
                WHERE u.active AND r.code = ?
                  AND (? = '*' OR ur.app_code = '*' OR ur.app_code = ?)
                  AND (ur.plant_id IS NULL OR ?::bigint IS NULL OR ur.plant_id = ?::bigint)
                ORDER BY u.id""", Long.class, roleCode, appCode, appCode, plantId, plantId);
    }

    /** Nama lengkap per id pengguna, untuk tampilan "Dibuat oleh" dsb. */
    public Map<Long, String> names(Collection<Long> userIds) {
        List<Long> ids = userIds.stream().filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        Map<Long, String> out = new HashMap<>();
        jdbc.query("SELECT id, full_name FROM sys.app_user WHERE id IN (" + in + ")",
                rs -> {
                    out.put(rs.getLong("id"), rs.getString("full_name"));
                });
        return out;
    }

    public String name(Long userId) {
        return userId == null ? null : names(List.of(userId)).get(userId);
    }

    public Long userIdOfEmployee(Long employeeId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM sys.app_user WHERE employee_id = ? AND active ORDER BY id LIMIT 1", Long.class, employeeId);
        return ids.isEmpty() ? null : ids.getFirst();
    }
}
