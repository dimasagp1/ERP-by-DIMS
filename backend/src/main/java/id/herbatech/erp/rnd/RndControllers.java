package id.herbatech.erp.rnd;

import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

final class RndControllers {
    private RndControllers() {}

    @RestController
    @RequestMapping("/api/rnd/projects")
    static class ProjectController extends DocumentApi<Project> {
        public ProjectController(ProjectHandler handler, ProjectRepository repo, Support support) {
            super(handler, repo, support, Project.class);
        }
    }

    @Component
    static class ProjectHandler implements DocumentHandler<Project> {
        private final ProjectRepository repo;
        ProjectHandler(ProjectRepository repo) { this.repo = repo; }
        @Override public String docType() { return "PRJ"; }
        @Override public String periodModule() { return "RND"; }
        @Override public Project load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public Project save(Project doc) { return repo.save(doc); }
        @Override public String summary(Project doc) { return doc.getName() != null ? doc.getName() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/rnd/formulas")
    static class FormulaController extends DocumentApi<Formula> {
        public FormulaController(FormulaHandler handler, FormulaRepository repo, Support support) {
            super(handler, repo, support, Formula.class);
        }
    }

    @Component
    static class FormulaHandler implements DocumentHandler<Formula> {
        private final FormulaRepository repo;
        FormulaHandler(FormulaRepository repo) { this.repo = repo; }
        @Override public String docType() { return "FML"; }
        @Override public String periodModule() { return "RND"; }
        @Override public Formula load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public Formula save(Formula doc) { return repo.save(doc); }
        @Override public String summary(Formula doc) { return doc.getFormulaVersion() != null ? "v" + doc.getFormulaVersion() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/rnd/trials")
    static class TrialController extends DocumentApi<Trial> {
        public TrialController(TrialHandler handler, TrialRepository repo, Support support) {
            super(handler, repo, support, Trial.class);
        }
    }

    @Component
    static class TrialHandler implements DocumentHandler<Trial> {
        private final TrialRepository repo;
        TrialHandler(TrialRepository repo) { this.repo = repo; }
        @Override public String docType() { return "TRL"; }
        @Override public String periodModule() { return "RND"; }
        @Override public Trial load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public Trial save(Trial doc) { return repo.save(doc); }
        @Override public String summary(Trial doc) { return doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/rnd/specs")
    static class ProductSpecController extends DocumentApi<ProductSpec> {
        public ProductSpecController(ProductSpecHandler handler, ProductSpecRepository repo, Support support) {
            super(handler, repo, support, ProductSpec.class);
        }
    }

    @Component
    static class ProductSpecHandler implements DocumentHandler<ProductSpec> {
        private final ProductSpecRepository repo;
        ProductSpecHandler(ProductSpecRepository repo) { this.repo = repo; }
        @Override public String docType() { return "SPEC"; }
        @Override public String periodModule() { return "RND"; }
        @Override public ProductSpec load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public ProductSpec save(ProductSpec doc) { return repo.save(doc); }
        @Override public String summary(ProductSpec doc) { return doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/rnd/registrations")
    static class ProductRegistrationController extends DocumentApi<ProductRegistration> {
        public ProductRegistrationController(ProductRegistrationHandler handler, ProductRegistrationRepository repo, Support support) {
            super(handler, repo, support, ProductRegistration.class);
        }
    }

    @Component
    static class ProductRegistrationHandler implements DocumentHandler<ProductRegistration> {
        private final ProductRegistrationRepository repo;
        ProductRegistrationHandler(ProductRegistrationRepository repo) { this.repo = repo; }
        @Override public String docType() { return "REG"; }
        @Override public String periodModule() { return "RND"; }
        @Override public ProductRegistration load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public ProductRegistration save(ProductRegistration doc) { return repo.save(doc); }
        @Override public String summary(ProductRegistration doc) { return doc.getRegistrationNo() != null ? doc.getRegistrationNo() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/rnd/artworks")
    static class ArtworkController extends DocumentApi<Artwork> {
        public ArtworkController(ArtworkHandler handler, ArtworkRepository repo, Support support) {
            super(handler, repo, support, Artwork.class);
        }
    }

    @Component
    static class ArtworkHandler implements DocumentHandler<Artwork> {
        private final ArtworkRepository repo;
        ArtworkHandler(ArtworkRepository repo) { this.repo = repo; }
        @Override public String docType() { return "ART"; }
        @Override public String periodModule() { return "RND"; }
        @Override public Artwork load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public Artwork save(Artwork doc) { return repo.save(doc); }
        @Override public String summary(Artwork doc) { return doc.getArtworkCode() != null ? doc.getArtworkCode() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/rnd/cost-estimates")
    static class CostEstimateController extends DocumentApi<CostEstimate> {
        public CostEstimateController(CostEstimateHandler handler, CostEstimateRepository repo, Support support) {
            super(handler, repo, support, CostEstimate.class);
        }
    }

    @Component
    static class CostEstimateHandler implements DocumentHandler<CostEstimate> {
        private final CostEstimateRepository repo;
        CostEstimateHandler(CostEstimateRepository repo) { this.repo = repo; }
        @Override public String docType() { return "CE"; }
        @Override public String periodModule() { return "RND"; }
        @Override public CostEstimate load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public CostEstimate save(CostEstimate doc) { return repo.save(doc); }
        @Override public String summary(CostEstimate doc) { return doc.getDocNo(); }
    }
}
