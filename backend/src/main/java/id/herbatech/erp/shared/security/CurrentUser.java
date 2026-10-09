package id.herbatech.erp.shared.security;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pengguna yang sedang login beserta semua penugasan perannya. Dibentuk sekali per permintaan
 * (dengan cache singkat) dan dipakai oleh {@link PermissionService}.
 */
public record CurrentUser(
        Long id,
        String username,
        String fullName,
        Long employeeId,
        Long defaultPlantId,
        Long plantId,
        List<Grant> grants) {

    /** Satu penugasan: peran × aplikasi × plant, plus pengecualian per menu milik peran itu. */
    public record Grant(Long roleId, String roleCode, String roleName, ViewScope viewScope, Set<Action> actions,
                        String appCode, Long plantId, Map<String, Map<Action, Boolean>> menuOverrides) {

        public boolean coversApp(String app) {
            return "*".equals(appCode) || appCode.equalsIgnoreCase(app);
        }

        public boolean coversPlant(Long plant) {
            return plantId == null || plant == null || plantId.equals(plant);
        }
    }

    public CurrentUser withPlant(Long plant) {
        return new CurrentUser(id, username, fullName, employeeId, defaultPlantId, plant, grants);
    }
}
