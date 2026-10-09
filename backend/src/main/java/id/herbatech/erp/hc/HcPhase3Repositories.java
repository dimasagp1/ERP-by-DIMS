package id.herbatech.erp.hc;

import id.herbatech.erp.shared.document.DocumentRepository;
import org.springframework.stereotype.Repository;

interface HcPhase3Repositories {}

@Repository interface RecruitmentRequestRepository extends DocumentRepository<RecruitmentRequest> {}
@Repository interface TrainingRecordRepository extends DocumentRepository<TrainingRecord> {}
@Repository interface DisciplinaryActionRepository extends DocumentRepository<DisciplinaryAction> {}
