package id.herbatech.erp.shared.security;

import id.herbatech.erp.shared.web.MasterRepository;

import java.util.Optional;

public interface RoleRepository extends MasterRepository<Role> {

    Optional<Role> findByCode(String code);
}
