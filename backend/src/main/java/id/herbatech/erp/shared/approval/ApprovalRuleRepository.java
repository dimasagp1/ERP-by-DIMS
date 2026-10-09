package id.herbatech.erp.shared.approval;

import id.herbatech.erp.shared.web.MasterRepository;

import java.util.List;

public interface ApprovalRuleRepository extends MasterRepository<ApprovalRule> {

    List<ApprovalRule> findByDocTypeCodeAndActiveTrueOrderByLevelAsc(String docTypeCode);

    List<ApprovalRule> findByDocTypeCodeInOrderByDocTypeCodeAscLevelAsc(List<String> docTypeCodes);
}
