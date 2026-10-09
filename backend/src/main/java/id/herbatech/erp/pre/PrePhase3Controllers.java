package id.herbatech.erp.pre;

import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.web.MasterController;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

final class PrePhase3Controllers {
    private PrePhase3Controllers() {}

    @RestController
    @RequestMapping("/api/pre/batch-records")
    static class BatchRecordController extends DocumentApi<BatchRecord> {
        public BatchRecordController(BatchRecordHandler handler, BatchRecordRepository repo, Support support) {
            super(handler, repo, support, BatchRecord.class);
        }
    }

    @Component
    static class BatchRecordHandler implements DocumentHandler<BatchRecord> {
        private final BatchRecordRepository repo;
        BatchRecordHandler(BatchRecordRepository repo) { this.repo = repo; }
        @Override public String docType() { return "BMR"; }
        @Override public String periodModule() { return "PRE"; }
        @Override public BatchRecord load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public BatchRecord save(BatchRecord doc) { return repo.save(doc); }
        @Override public String summary(BatchRecord doc) { return doc.getBatchNo() != null ? doc.getBatchNo() + " - " + doc.getStageName() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/pre/dispensings")
    static class DispensingController extends DocumentApi<Dispensing> {
        public DispensingController(DispensingHandler handler, DispensingRepository repo, Support support) {
            super(handler, repo, support, Dispensing.class);
        }
    }

    @Component
    static class DispensingHandler implements DocumentHandler<Dispensing> {
        private final DispensingRepository repo;
        DispensingHandler(DispensingRepository repo) { this.repo = repo; }
        @Override public String docType() { return "DSP"; }
        @Override public String periodModule() { return "PRE"; }
        @Override public Dispensing load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public Dispensing save(Dispensing doc) { return repo.save(doc); }
        @Override public String summary(Dispensing doc) { return doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/pre/ipc-entries")
    static class IpcEntryController extends DocumentApi<IpcEntry> {
        public IpcEntryController(IpcEntryHandler handler, IpcEntryRepository repo, Support support) {
            super(handler, repo, support, IpcEntry.class);
        }
    }

    @Component
    static class IpcEntryHandler implements DocumentHandler<IpcEntry> {
        private final IpcEntryRepository repo;
        IpcEntryHandler(IpcEntryRepository repo) { this.repo = repo; }
        @Override public String docType() { return "IPC"; }
        @Override public String periodModule() { return "PRE"; }
        @Override public IpcEntry load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public IpcEntry save(IpcEntry doc) { return repo.save(doc); }
        @Override public String summary(IpcEntry doc) { return doc.getParamName() != null ? doc.getParamName() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/pre/downtimes")
    static class DowntimeLogController extends DocumentApi<DowntimeLog> {
        public DowntimeLogController(DowntimeLogHandler handler, DowntimeLogRepository repo, Support support) {
            super(handler, repo, support, DowntimeLog.class);
        }
    }

    @Component
    static class DowntimeLogHandler implements DocumentHandler<DowntimeLog> {
        private final DowntimeLogRepository repo;
        DowntimeLogHandler(DowntimeLogRepository repo) { this.repo = repo; }
        @Override public String docType() { return "DT"; }
        @Override public String periodModule() { return "PRE"; }
        @Override public DowntimeLog load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public DowntimeLog save(DowntimeLog doc) { return repo.save(doc); }
        @Override public String summary(DowntimeLog doc) { return doc.getReasonCategory() != null ? doc.getReasonCategory() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/pre/line-clearances")
    static class LineClearanceController extends DocumentApi<LineClearance> {
        public LineClearanceController(LineClearanceHandler handler, LineClearanceRepository repo, Support support) {
            super(handler, repo, support, LineClearance.class);
        }
    }

    @Component
    static class LineClearanceHandler implements DocumentHandler<LineClearance> {
        private final LineClearanceRepository repo;
        LineClearanceHandler(LineClearanceRepository repo) { this.repo = repo; }
        @Override public String docType() { return "LCL"; }
        @Override public String periodModule() { return "PRE"; }
        @Override public LineClearance load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public LineClearance save(LineClearance doc) { return repo.save(doc); }
        @Override public String summary(LineClearance doc) { return doc.getStatusResult() != null ? doc.getStatusResult() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/pre/work-requests")
    static class MaintenanceWorkRequestController extends DocumentApi<MaintenanceWorkRequest> {
        public MaintenanceWorkRequestController(MaintenanceWorkRequestHandler handler, MaintenanceWorkRequestRepository repo, Support support) {
            super(handler, repo, support, MaintenanceWorkRequest.class);
        }
    }

    @Component
    static class MaintenanceWorkRequestHandler implements DocumentHandler<MaintenanceWorkRequest> {
        private final MaintenanceWorkRequestRepository repo;
        MaintenanceWorkRequestHandler(MaintenanceWorkRequestRepository repo) { this.repo = repo; }
        @Override public String docType() { return "WRQ"; }
        @Override public String periodModule() { return "PRE"; }
        @Override public MaintenanceWorkRequest load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public MaintenanceWorkRequest save(MaintenanceWorkRequest doc) { return repo.save(doc); }
        @Override public String summary(MaintenanceWorkRequest doc) { return doc.getDescription() != null ? doc.getDescription() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/pre/maintenance-orders")
    static class MaintenanceOrderController extends DocumentApi<MaintenanceOrder> {
        public MaintenanceOrderController(MaintenanceOrderHandler handler, MaintenanceOrderRepository repo, Support support) {
            super(handler, repo, support, MaintenanceOrder.class);
        }
    }

    @Component
    static class MaintenanceOrderHandler implements DocumentHandler<MaintenanceOrder> {
        private final MaintenanceOrderRepository repo;
        MaintenanceOrderHandler(MaintenanceOrderRepository repo) { this.repo = repo; }
        @Override public String docType() { return "MNT"; }
        @Override public String periodModule() { return "PRE"; }
        @Override public MaintenanceOrder load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public MaintenanceOrder save(MaintenanceOrder doc) { return repo.save(doc); }
        @Override public String summary(MaintenanceOrder doc) { return doc.getMaintenanceType() != null ? doc.getMaintenanceType() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/pre/calibrations")
    static class CalibrationController extends DocumentApi<Calibration> {
        public CalibrationController(CalibrationHandler handler, CalibrationRepository repo, Support support) {
            super(handler, repo, support, Calibration.class);
        }
    }

    @Component
    static class CalibrationHandler implements DocumentHandler<Calibration> {
        private final CalibrationRepository repo;
        CalibrationHandler(CalibrationRepository repo) { this.repo = repo; }
        @Override public String docType() { return "CAL"; }
        @Override public String periodModule() { return "PRE"; }
        @Override public Calibration load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public Calibration save(Calibration doc) { return repo.save(doc); }
        @Override public String summary(Calibration doc) { return doc.getCertNo() != null ? doc.getCertNo() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/pre/machines")
    static class MachineController extends MasterController<Machine> {
        public MachineController(MachineRepository repo, PermissionService perm) {
            super(repo, perm, Machine.class);
        }
        @Override protected String menuCode() { return "PRE-20"; }
    }

    @RestController
    @RequestMapping("/api/pre/utility-logs")
    static class UtilityLogController extends MasterController<UtilityLog> {
        public UtilityLogController(UtilityLogRepository repo, PermissionService perm) {
            super(repo, perm, UtilityLog.class);
        }
        @Override protected String menuCode() { return "PRE-26"; }
    }
}
