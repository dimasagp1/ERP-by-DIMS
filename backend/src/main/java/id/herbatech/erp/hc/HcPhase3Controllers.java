package id.herbatech.erp.hc;

import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

final class HcPhase3Controllers {
    private HcPhase3Controllers() {}

    @RestController
    @RequestMapping("/api/hc/recruitments")
    static class RecruitmentRequestController extends DocumentApi<RecruitmentRequest> {
        public RecruitmentRequestController(RecruitmentRequestHandler handler, RecruitmentRequestRepository repo, Support support) {
            super(handler, repo, support, RecruitmentRequest.class);
        }
    }

    @Component
    static class RecruitmentRequestHandler implements DocumentHandler<RecruitmentRequest> {
        private final RecruitmentRequestRepository repo;
        RecruitmentRequestHandler(RecruitmentRequestRepository repo) { this.repo = repo; }
        @Override public String docType() { return "RECR"; }
        @Override public String periodModule() { return "HC"; }
        @Override public RecruitmentRequest load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public RecruitmentRequest save(RecruitmentRequest doc) { return repo.save(doc); }
        @Override public String summary(RecruitmentRequest doc) { return doc.getJustification() != null ? doc.getJustification() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/hc/trainings")
    static class TrainingRecordController extends DocumentApi<TrainingRecord> {
        public TrainingRecordController(TrainingRecordHandler handler, TrainingRecordRepository repo, Support support) {
            super(handler, repo, support, TrainingRecord.class);
        }
    }

    @Component
    static class TrainingRecordHandler implements DocumentHandler<TrainingRecord> {
        private final TrainingRecordRepository repo;
        TrainingRecordHandler(TrainingRecordRepository repo) { this.repo = repo; }
        @Override public String docType() { return "TRN"; }
        @Override public String periodModule() { return "HC"; }
        @Override public TrainingRecord load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public TrainingRecord save(TrainingRecord doc) { return repo.save(doc); }
        @Override public String summary(TrainingRecord doc) { return doc.getTopic() != null ? doc.getTopic() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/hc/disciplines")
    static class DisciplinaryActionController extends DocumentApi<DisciplinaryAction> {
        public DisciplinaryActionController(DisciplinaryActionHandler handler, DisciplinaryActionRepository repo, Support support) {
            super(handler, repo, support, DisciplinaryAction.class);
        }
    }

    @Component
    static class DisciplinaryActionHandler implements DocumentHandler<DisciplinaryAction> {
        private final DisciplinaryActionRepository repo;
        DisciplinaryActionHandler(DisciplinaryActionRepository repo) { this.repo = repo; }
        @Override public String docType() { return "SP"; }
        @Override public String periodModule() { return "HC"; }
        @Override public DisciplinaryAction load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public DisciplinaryAction save(DisciplinaryAction doc) { return repo.save(doc); }
        @Override public String summary(DisciplinaryAction doc) { return doc.getActionLevel() != null ? doc.getActionLevel() + ": " + doc.getDescription() : doc.getDocNo(); }
    }
}
