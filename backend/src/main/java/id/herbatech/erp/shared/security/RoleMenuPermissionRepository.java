package id.herbatech.erp.shared.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RoleMenuPermissionRepository extends JpaRepository<RoleMenuPermission, Long> {

    List<RoleMenuPermission> findByRoleIdIn(Collection<Long> roleIds);

    List<RoleMenuPermission> findByRoleId(Long roleId);
}
