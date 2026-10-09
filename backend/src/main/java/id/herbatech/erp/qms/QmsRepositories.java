package id.herbatech.erp.qms;

import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.web.MasterRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

interface QmsRepositories {}

@Repository interface QmsDocumentRepository extends DocumentRepository<QmsDocument> {}
@Repository interface DocVersionRepository extends JpaRepository<DocVersion, Long> {}
@Repository interface ChangeControlRepository extends DocumentRepository<ChangeControl> {}
@Repository interface DeviationRepository extends DocumentRepository<Deviation> {}
@Repository interface CapaRepository extends DocumentRepository<Capa> {}
@Repository interface ComplaintRepository extends DocumentRepository<Complaint> {}
@Repository interface SampleRepository extends MasterRepository<Sample> {}
@Repository interface TestResultRepository extends MasterRepository<TestResult> {}
@Repository interface CoaRepository extends MasterRepository<Coa> {}
@Repository interface BatchReleaseRepository extends DocumentRepository<BatchRelease> {}
@Repository interface StabilityStudyRepository extends DocumentRepository<StabilityStudy> {}