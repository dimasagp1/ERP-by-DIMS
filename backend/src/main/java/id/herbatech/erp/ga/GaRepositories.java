package id.herbatech.erp.ga;

import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.web.MasterRepository;
import org.springframework.stereotype.Repository;

interface GaRepositories {}

@Repository interface InventoryAssetRepository extends MasterRepository<InventoryAsset> {}
@Repository interface GaServiceRequestRepository extends DocumentRepository<GaServiceRequest> {}
@Repository interface VehicleBookingRepository extends DocumentRepository<VehicleBooking> {}
@Repository interface RoomBookingRepository extends MasterRepository<RoomBooking> {}
@Repository interface GatePassRepository extends DocumentRepository<GatePass> {}
@Repository interface CompanyPermitRepository extends MasterRepository<CompanyPermit> {}
@Repository interface HsseIncidentRepository extends DocumentRepository<HsseIncident> {}
