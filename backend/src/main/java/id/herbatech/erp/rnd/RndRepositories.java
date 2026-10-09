package id.herbatech.erp.rnd;

import id.herbatech.erp.shared.document.DocumentRepository;
import org.springframework.stereotype.Repository;

interface RndRepositories {}

@Repository interface ProjectRepository extends DocumentRepository<Project> {}
@Repository interface FormulaRepository extends DocumentRepository<Formula> {}
@Repository interface TrialRepository extends DocumentRepository<Trial> {}
@Repository interface ProductSpecRepository extends DocumentRepository<ProductSpec> {}
@Repository interface ProductRegistrationRepository extends DocumentRepository<ProductRegistration> {}
@Repository interface ArtworkRepository extends DocumentRepository<Artwork> {}
@Repository interface CostEstimateRepository extends DocumentRepository<CostEstimate> {}
