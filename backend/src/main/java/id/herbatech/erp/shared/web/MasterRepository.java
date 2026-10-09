package id.herbatech.erp.shared.web;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/** Repository standar untuk master data yang dipakai {@link MasterController}. */
@NoRepositoryBean
public interface MasterRepository<E> extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {
}
