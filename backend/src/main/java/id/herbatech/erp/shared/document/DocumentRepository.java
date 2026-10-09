package id.herbatech.erp.shared.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/** Repository standar dokumen transaksi yang dipakai {@link DocumentApi}. */
@NoRepositoryBean
public interface DocumentRepository<D> extends JpaRepository<D, Long>, JpaSpecificationExecutor<D> {
}
