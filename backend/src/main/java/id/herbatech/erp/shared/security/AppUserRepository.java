package id.herbatech.erp.shared.security;

import id.herbatech.erp.shared.web.MasterRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends MasterRepository<AppUser> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    Optional<AppUser> findFirstByEmployeeIdAndActiveTrue(Long employeeId);

    List<AppUser> findByIdIn(Collection<Long> ids);
}
