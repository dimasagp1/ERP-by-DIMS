package id.herbatech.erp.sys;

import id.herbatech.erp.shared.config.ErpProperties;
import id.herbatech.erp.shared.security.AppUser;
import id.herbatech.erp.shared.security.AppUserRepository;
import id.herbatech.erp.shared.security.RoleRepository;
import id.herbatech.erp.shared.security.UserRole;
import id.herbatech.erp.shared.security.UserRoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Membuat pengguna demo (satu per peran/departemen) bila database belum punya pengguna.
 * Hanya aktif bila {@code erp.demo.seed-users=true} (profil dev/test). Daftar akun ada di README.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "erp.demo", name = "seed-users", havingValue = "true")
class DemoDataSeeder implements ApplicationRunner {

    /** username, NIK karyawan, lalu pasangan peran:aplikasi. */
    static final List<String[]> USERS = List.of(
            new String[]{"admin", "E0099", "ADMIN:SYS"},
            new String[]{"direktur", "E0001", "DIRECTOR:*"},
            new String[]{"dir.keuangan", "E0002", "DIRECTOR:*"},
            new String[]{"dir.ops", "E0003", "DIRECTOR:*"},
            new String[]{"auditor", "E0090", "AUDITOR:*"},
            new String[]{"fin.manager", "E0010", "MANAGER:FIN"},
            new String[]{"fin.spv", "E0011", "SUPERVISOR:FIN"},
            new String[]{"fin.staf", "E0012", "OPERATOR:FIN"},
            new String[]{"prc.manager", "E0020", "MANAGER:PRC"},
            new String[]{"prc.staf", "E0021", "OPERATOR:PRC"},
            new String[]{"pre.manager", "E0030", "MANAGER:PRE"},
            new String[]{"pre.spv", "E0031", "SUPERVISOR:PRE"},
            new String[]{"pre.operator", "E0032", "OPERATOR:PRE"},
            new String[]{"qa.manager", "E0040", "MANAGER:QMS"},
            new String[]{"qa.release", "E0041", "QA_RELEASE:QMS", "QA_RELEASE:SCM", "QA_RELEASE:PRE"},
            new String[]{"scm.manager", "E0050", "MANAGER:SCM"},
            new String[]{"scm.spv", "E0051", "SUPERVISOR:SCM"},
            new String[]{"hc.manager", "E0060", "MANAGER:HC"},
            new String[]{"hc.payroll", "E0061", "PAYROLL:HC"},
            new String[]{"ga.manager", "E0070", "MANAGER:GA"},
            new String[]{"ga.staf", "E0071", "OPERATOR:GA"},
            new String[]{"rnd.manager", "E0080", "MANAGER:RND"});

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final ErpProperties props;

    DemoDataSeeder(AppUserRepository users, RoleRepository roles, UserRoleRepository userRoles, PasswordEncoder encoder,
                   JdbcTemplate jdbc, ErpProperties props) {
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String password = props.demo().password();
        if (password == null || password.length() < 8) {
            log.warn("erp.demo.password kosong/terlalu pendek; pengguna demo tidak dibuat");
            return;
        }
        String hash = encoder.encode(password);
        Long plant = jdbc.queryForObject("SELECT id FROM sys.plant WHERE code = 'P1'", Long.class);
        for (String[] u : USERS) {
            var emp = jdbc.queryForMap("SELECT id, name FROM hc.employee WHERE nik = ?", u[1]);
            AppUser user = new AppUser();
            user.setUsername(u[0]);
            user.setFullName((String) emp.get("name"));
            user.setEmployeeId(((Number) emp.get("id")).longValue());
            user.setPasswordHash(hash);
            user.setDefaultPlantId(plant);
            user.setEmail(u[0] + "@demo.local");
            Long userId = users.save(user).getId();
            for (int i = 2; i < u.length; i++) {
                String[] ra = u[i].split(":");
                Long roleId = roles.findByCode(ra[0]).orElseThrow().getId();
                userRoles.save(new UserRole(userId, roleId, ra[1], null));
            }
        }
        log.info("Membuat {} pengguna demo", USERS.size());
    }
}
