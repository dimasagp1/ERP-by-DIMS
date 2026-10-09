package id.herbatech.erp.qms;

import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.web.MasterController;
import id.herbatech.erp.shared.security.PermissionService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

final class QmsControllers {

    private QmsControllers() {}

    @RestController
    @RequestMapping("/api/qms/documents")
    static class QmsDocumentController extends DocumentApi<QmsDocument> {
        public QmsDocumentController(QmsDocumentHandler handler, QmsDocumentRepository repo, Support support) {
            super(handler, repo, support, QmsDocument.class);
        }
    }

    @Component
    static class QmsDocumentHandler implements DocumentHandler<QmsDocument> {
        private final QmsDocumentRepository repo;
        QmsDocumentHandler(QmsDocumentRepository repo) { this.repo = repo; }
        @Override public String docType() { return "QMSDOC"; }
        @Override public String periodModule() { return "QMS"; }
        @Override public QmsDocument load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public QmsDocument save(QmsDocument doc) { return repo.save(doc); }
        @Override public String summary(QmsDocument doc) { return doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/qms/change-controls")
    static class ChangeControlController extends DocumentApi<ChangeControl> {
        public ChangeControlController(ChangeControlHandler handler, ChangeControlRepository repo, Support support) {
            super(handler, repo, support, ChangeControl.class);
        }
    }

    @Component
    static class ChangeControlHandler implements DocumentHandler<ChangeControl> {
        private final ChangeControlRepository repo;
        ChangeControlHandler(ChangeControlRepository repo) { this.repo = repo; }
        @Override public String docType() { return "CC"; }
        @Override public String periodModule() { return "QMS"; }
        @Override public ChangeControl load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public ChangeControl save(ChangeControl doc) { return repo.save(doc); }
        @Override public String summary(ChangeControl doc) { return doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/qms/deviations")
    static class DeviationController extends DocumentApi<Deviation> {
        public DeviationController(DeviationHandler handler, DeviationRepository repo, Support support) {
            super(handler, repo, support, Deviation.class);
        }
    }

    @Component
    static class DeviationHandler implements DocumentHandler<Deviation> {
        private final DeviationRepository repo;
        DeviationHandler(DeviationRepository repo) { this.repo = repo; }
        @Override public String docType() { return "DEV"; }
        @Override public String periodModule() { return "QMS"; }
        @Override public Deviation load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public Deviation save(Deviation doc) { return repo.save(doc); }
        @Override public String summary(Deviation doc) { return doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/qms/capas")
    static class CapaController extends DocumentApi<Capa> {
        public CapaController(CapaHandler handler, CapaRepository repo, Support support) {
            super(handler, repo, support, Capa.class);
        }
    }

    @Component
    static class CapaHandler implements DocumentHandler<Capa> {
        private final CapaRepository repo;
        CapaHandler(CapaRepository repo) { this.repo = repo; }
        @Override public String docType() { return "CAPA"; }
        @Override public String periodModule() { return "QMS"; }
        @Override public Capa load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public Capa save(Capa doc) { return repo.save(doc); }
        @Override public String summary(Capa doc) { return doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/qms/complaints")
    static class ComplaintController extends DocumentApi<Complaint> {
        public ComplaintController(ComplaintHandler handler, ComplaintRepository repo, Support support) {
            super(handler, repo, support, Complaint.class);
        }
    }

    @Component
    static class ComplaintHandler implements DocumentHandler<Complaint> {
        private final ComplaintRepository repo;
        ComplaintHandler(ComplaintRepository repo) { this.repo = repo; }
        @Override public String docType() { return "CMP"; }
        @Override public String periodModule() { return "QMS"; }
        @Override public Complaint load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public Complaint save(Complaint doc) { return repo.save(doc); }
        @Override public String summary(Complaint doc) { return doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/qms/samples")
    static class SampleController extends MasterController<Sample> {
        public SampleController(SampleRepository repo, PermissionService perm) { super(repo, perm, Sample.class); }
        @Override protected String menuCode() { return "QMS-10"; }
    }

    @RestController
    @RequestMapping("/api/qms/test-results")
    static class TestResultController extends MasterController<TestResult> {
        public TestResultController(TestResultRepository repo, PermissionService perm) { super(repo, perm, TestResult.class); }
        @Override protected String menuCode() { return "QMS-11"; }
    }

    @RestController
    @RequestMapping("/api/qms/coas")
    static class CoaController extends MasterController<Coa> {
        public CoaController(CoaRepository repo, PermissionService perm) { super(repo, perm, Coa.class); }
        @Override protected String menuCode() { return "QMS-13"; }
    }

    @RestController
    @RequestMapping("/api/qms/batch-releases")
    static class BatchReleaseController extends DocumentApi<BatchRelease> {
        public BatchReleaseController(BatchReleaseHandler handler, BatchReleaseRepository repo, Support support) {
            super(handler, repo, support, BatchRelease.class);
        }
    }

    @Component
    static class BatchReleaseHandler implements DocumentHandler<BatchRelease> {
        private final BatchReleaseRepository repo;
        BatchReleaseHandler(BatchReleaseRepository repo) { this.repo = repo; }
        @Override public String docType() { return "BR"; }
        @Override public String periodModule() { return "QMS"; }
        @Override public BatchRelease load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public BatchRelease save(BatchRelease doc) { return repo.save(doc); }
        @Override public String summary(BatchRelease doc) { return doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/qms/stability-studies")
    static class StabilityStudyController extends DocumentApi<StabilityStudy> {
        public StabilityStudyController(StabilityStudyHandler handler, StabilityStudyRepository repo, Support support) {
            super(handler, repo, support, StabilityStudy.class);
        }
    }

    @Component
    static class StabilityStudyHandler implements DocumentHandler<StabilityStudy> {
        private final StabilityStudyRepository repo;
        StabilityStudyHandler(StabilityStudyRepository repo) { this.repo = repo; }
        @Override public String docType() { return "STB"; }
        @Override public String periodModule() { return "QMS"; }
        @Override public StabilityStudy load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public StabilityStudy save(StabilityStudy doc) { return repo.save(doc); }
        @Override public String summary(StabilityStudy doc) { return doc.getDocNo(); }
    }
}
