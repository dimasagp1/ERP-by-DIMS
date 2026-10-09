package id.herbatech.erp.pre;

import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.web.MasterRepository;
import org.springframework.stereotype.Repository;

interface PrePhase3Repositories {}

@Repository interface BatchRecordRepository extends DocumentRepository<BatchRecord> {}
@Repository interface DispensingRepository extends DocumentRepository<Dispensing> {}
@Repository interface IpcEntryRepository extends DocumentRepository<IpcEntry> {}
@Repository interface DowntimeLogRepository extends DocumentRepository<DowntimeLog> {}
@Repository interface LineClearanceRepository extends DocumentRepository<LineClearance> {}
@Repository interface MachineRepository extends MasterRepository<Machine> {}
@Repository interface MaintenanceWorkRequestRepository extends DocumentRepository<MaintenanceWorkRequest> {}
@Repository interface MaintenanceOrderRepository extends DocumentRepository<MaintenanceOrder> {}
@Repository interface CalibrationRepository extends DocumentRepository<Calibration> {}
@Repository interface UtilityLogRepository extends MasterRepository<UtilityLog> {}
