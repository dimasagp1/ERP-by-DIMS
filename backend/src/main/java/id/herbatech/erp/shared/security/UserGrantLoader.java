package id.herbatech.erp.shared.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Memuat pengguna + penugasan peran dari database, dengan cache singkat agar tiap permintaan API
 * tidak melakukan query hak akses berulang. Cache dibersihkan saat admin mengubah peran pengguna.
 */
@Component
public class UserGrantLoader {

    private static final Duration TTL = Duration.ofSeconds(60);

    private record Entry(CurrentUser user, long loadedAt) {
    }

    private final JdbcTemplate jdbc;
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public UserGrantLoader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return pengguna aktif, atau null bila tidak ada / nonaktif. */
    public CurrentUser load(Long userId) {
        Entry e = cache.get(userId);
        if (e != null && System.currentTimeMillis() - e.loadedAt < TTL.toMillis()) {
            return e.user;
        }
        CurrentUser user = query(userId);
        if (user != null) {
            cache.put(userId, new Entry(user, System.currentTimeMillis()));
        } else {
            cache.remove(userId);
        }
        return user;
    }

    public void evict(Long userId) {
        cache.remove(userId);
    }

    public void evictAll() {
        cache.clear();
    }

    private CurrentUser query(Long userId) {
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT id, username, full_name, employee_id, default_plant_id FROM sys.app_user WHERE id = ? AND active",
                userId);
        if (users.isEmpty()) {
            return null;
        }
        Map<String, Object> u = users.getFirst();

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ur.role_id, r.code, r.name, r.view_scope, r.actions, ur.app_code, ur.plant_id
                FROM sys.user_role ur JOIN sys.role r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND r.active
                ORDER BY ur.id""", userId);

        Map<Long, Map<String, Map<Action, Boolean>>> overrides = new HashMap<>();
        if (!rows.isEmpty()) {
            String ids = rows.stream().map(r -> String.valueOf(r.get("role_id"))).distinct().collect(Collectors.joining(","));
            jdbc.query("SELECT role_id, menu_code, action, allowed FROM sys.role_menu_permission WHERE role_id IN (" + ids + ")",
                    rs -> {
                        overrides.computeIfAbsent(rs.getLong("role_id"), k -> new HashMap<>())
                                .computeIfAbsent(rs.getString("menu_code"), k -> new EnumMap<>(Action.class))
                                .put(Action.valueOf(rs.getString("action")), rs.getBoolean("allowed"));
                    });
        }

        List<CurrentUser.Grant> grants = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Long roleId = ((Number) r.get("role_id")).longValue();
            grants.add(new CurrentUser.Grant(
                    roleId,
                    (String) r.get("code"),
                    (String) r.get("name"),
                    ViewScope.valueOf((String) r.get("view_scope")),
                    parseActions((String) r.get("actions")),
                    (String) r.get("app_code"),
                    r.get("plant_id") == null ? null : ((Number) r.get("plant_id")).longValue(),
                    overrides.getOrDefault(roleId, Map.of())));
        }
        return new CurrentUser(
                ((Number) u.get("id")).longValue(),
                (String) u.get("username"),
                (String) u.get("full_name"),
                u.get("employee_id") == null ? null : ((Number) u.get("employee_id")).longValue(),
                u.get("default_plant_id") == null ? null : ((Number) u.get("default_plant_id")).longValue(),
                u.get("default_plant_id") == null ? null : ((Number) u.get("default_plant_id")).longValue(),
                List.copyOf(grants));
    }

    private static Set<Action> parseActions(String csv) {
        Set<Action> set = EnumSet.noneOf(Action.class);
        if (csv != null) {
            Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Action::valueOf).forEach(set::add);
        }
        return set;
    }
}
