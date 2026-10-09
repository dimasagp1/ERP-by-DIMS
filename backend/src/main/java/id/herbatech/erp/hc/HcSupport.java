package id.herbatech.erp.hc;

import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import id.herbatech.erp.shared.security.UserDirectory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Utilitas modul HC: karyawan milik pengguna login, nama karyawan, aturan layanan mandiri. */
@Component
class HcSupport {

    private final JdbcTemplate jdbc;
    private final PermissionService perm;
    private final UserDirectory users;

    HcSupport(JdbcTemplate jdbc, PermissionService perm, UserDirectory users) {
        this.jdbc = jdbc;
        this.perm = perm;
        this.users = users;
    }

    /** Karyawan yang terhubung dengan akun login; wajib ada untuk layanan mandiri. */
    Long myEmployeeId() {
        Long id = UserContext.current().employeeId();
        if (id == null) {
            throw new BusinessException("NO_EMPLOYEE", "Akun Anda belum terhubung ke data karyawan (HC-03). Hubungi HC.");
        }
        return id;
    }

    Long myEmployeeIdOrNull() {
        return UserContext.current().employeeId();
    }

    /** Staf HC boleh membuat dokumen untuk karyawan lain; karyawan biasa hanya untuk dirinya. */
    boolean actsForOthers(String menu) {
        return perm.has(menu, Action.CREATE);
    }

    Long userOfEmployee(Long employeeId) {
        return employeeId == null ? null : users.userIdOfEmployee(employeeId);
    }

    String employeeName(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        return jdbc.queryForList("SELECT nik || ' · ' || name FROM hc.employee WHERE id = ?", String.class, employeeId)
                .stream().findFirst().orElse("#" + employeeId);
    }

    Map<Long, String> employeeNames(Collection<Long> ids) {
        List<Long> list = ids.stream().filter(x -> x != null).distinct().toList();
        if (list.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = new HashMap<>();
        jdbc.query("SELECT id, nik || ' · ' || name AS n FROM hc.employee WHERE id IN ("
                        + list.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")",
                rs -> {
                    out.put(rs.getLong("id"), rs.getString("n"));
                });
        return out;
    }

    void requireActiveEmployee(Long employeeId) {
        String status = jdbc.queryForList("SELECT status FROM hc.employee WHERE id = ?", String.class, employeeId)
                .stream().findFirst().orElseThrow(() -> new BusinessException("EMPLOYEE", "Karyawan tidak ditemukan"));
        if (!"ACTIVE".equals(status)) {
            throw new BusinessException("EMPLOYEE", "Karyawan berstatus " + status + " tidak bisa diproses");
        }
    }
}
