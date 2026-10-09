package id.herbatech.erp.shared.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hak akses per menu × aksi × plant (PRD §2). Semua controller memanggil {@link #require} sebelum
 * membaca atau mengubah data, sehingga aturan akses berada di satu tempat.
 */
@Service("perm")
public class PermissionService {

    /** Kode aplikasi departemen, urutan sama dengan launcher. */
    public static final List<String> DEPARTMENT_APPS = List.of("PRE", "PRC", "FIN", "GA", "HC", "QMS", "SCM", "RND");
    public static final String ESS = "ESS";
    public static final String SYS = "SYS";

    /** Layanan Saya terbuka untuk semua karyawan. */
    private static final Set<Action> ESS_SELF = EnumSet.of(Action.VIEW, Action.CREATE, Action.EDIT, Action.SUBMIT);

    public static String appOf(String menuCode) {
        int dash = menuCode.indexOf('-');
        return (dash < 0 ? menuCode : menuCode.substring(0, dash)).toUpperCase();
    }

    public boolean has(String menuCode, Action action) {
        return has(UserContext.current(), menuCode, action);
    }

    public boolean has(CurrentUser user, String menuCode, Action action) {
        String app = appOf(menuCode);
        if (ESS.equals(app) && ESS_SELF.contains(action)) {
            return true;
        }
        for (CurrentUser.Grant g : user.grants()) {
            if (!g.coversApp(app) || !g.coversPlant(user.plantId())) {
                continue;
            }
            Map<Action, Boolean> ov = g.menuOverrides().get(menuCode);
            Boolean explicit = ov == null ? null : ov.get(action);
            if (explicit != null) {
                if (explicit) {
                    return true;
                }
                continue;
            }
            if (g.actions().contains(action)) {
                return true;
            }
        }
        return false;
    }

    public void require(String menuCode, Action action) {
        if (!has(menuCode, action)) {
            throw new AccessDeniedException("Anda tidak memiliki hak " + action.name().toLowerCase() + " di menu " + menuCode);
        }
    }

    /** Apakah pengguna memegang peran tertentu di aplikasi & plant (dipakai approval berbasis peran). */
    public boolean holdsRole(CurrentUser user, String roleCode, String app, Long plantId) {
        // app '*' pada aturan approval berarti peran tersebut di aplikasi mana pun (mis. Direktur).
        return user.grants().stream().anyMatch(g -> g.roleCode().equals(roleCode)
                && ("*".equals(app) || g.coversApp(app))
                && g.coversPlant(plantId));
    }

    /** Cakupan data terluas yang dimiliki pengguna di sebuah aplikasi. */
    public ViewScope viewScope(String app) {
        CurrentUser user = UserContext.current();
        ViewScope scope = null;
        for (CurrentUser.Grant g : user.grants()) {
            if (g.coversApp(app) && g.coversPlant(user.plantId()) && g.actions().contains(Action.VIEW)) {
                scope = scope == null ? g.viewScope() : scope.widest(g.viewScope());
            }
        }
        if (scope == null && ESS.equals(app)) {
            return ViewScope.OWN;
        }
        return scope == null ? ViewScope.OWN : scope;
    }

    /** Aplikasi yang ikonnya tampil di launcher. Layanan Saya selalu tampil. */
    public Set<String> visibleApps(CurrentUser user) {
        Set<String> apps = new LinkedHashSet<>();
        for (String app : DEPARTMENT_APPS) {
            if (hasAnyInApp(user, app)) {
                apps.add(app);
            }
        }
        apps.add(ESS);
        if (hasAnyInApp(user, SYS)) {
            apps.add(SYS);
        }
        return apps;
    }

    /** Pengaturan aplikasi (ikon gear) hanya untuk Manager & Admin (PRD §15.4). */
    public boolean canManageSettings(String app) {
        CurrentUser user = UserContext.current();
        return user.grants().stream().anyMatch(g -> g.coversApp(app) && g.coversPlant(user.plantId())
                && (g.roleCode().equals("MANAGER") || g.roleCode().equals("ADMIN")));
    }

    public void requireSettings(String app) {
        if (!canManageSettings(app)) {
            throw new AccessDeniedException("Pengaturan aplikasi " + app + " hanya untuk Manager atau Admin");
        }
    }

    private boolean hasAnyInApp(CurrentUser user, String app) {
        return user.grants().stream().anyMatch(g -> g.coversApp(app) && g.actions().contains(Action.VIEW));
    }
}
