package id.herbatech.erp.shared.document;

import id.herbatech.erp.shared.web.MasterRepository;

import java.util.List;
import java.util.Optional;

public interface DocTypeRepository extends MasterRepository<DocType> {

    Optional<DocType> findByCode(String code);

    List<DocType> findByAppCodeOrderByCode(String appCode);
}
