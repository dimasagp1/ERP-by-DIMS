package id.herbatech.erp.ga;

import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.web.MasterController;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

final class GaControllers {
    private GaControllers() {}

    @RestController
    @RequestMapping("/api/ga/service-requests")
    static class ServiceRequestController extends DocumentApi<GaServiceRequest> {
        public ServiceRequestController(ServiceRequestHandler handler, GaServiceRequestRepository repo, Support support) {
            super(handler, repo, support, GaServiceRequest.class);
        }
    }

    @Component
    static class ServiceRequestHandler implements DocumentHandler<GaServiceRequest> {
        private final GaServiceRequestRepository repo;
        ServiceRequestHandler(GaServiceRequestRepository repo) { this.repo = repo; }
        @Override public String docType() { return "REQ-GA"; }
        @Override public String periodModule() { return "GA"; }
        @Override public GaServiceRequest load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public GaServiceRequest save(GaServiceRequest doc) { return repo.save(doc); }
        @Override public String summary(GaServiceRequest doc) { return doc.getDescription() != null ? doc.getDescription() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/ga/vehicle-bookings")
    static class VehicleBookingController extends DocumentApi<VehicleBooking> {
        public VehicleBookingController(VehicleBookingHandler handler, VehicleBookingRepository repo, Support support) {
            super(handler, repo, support, VehicleBooking.class);
        }
    }

    @Component
    static class VehicleBookingHandler implements DocumentHandler<VehicleBooking> {
        private final VehicleBookingRepository repo;
        VehicleBookingHandler(VehicleBookingRepository repo) { this.repo = repo; }
        @Override public String docType() { return "VHB"; }
        @Override public String periodModule() { return "GA"; }
        @Override public VehicleBooking load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public VehicleBooking save(VehicleBooking doc) { return repo.save(doc); }
        @Override public String summary(VehicleBooking doc) { return doc.getDestination() != null ? doc.getDestination() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/ga/gate-passes")
    static class GatePassController extends DocumentApi<GatePass> {
        public GatePassController(GatePassHandler handler, GatePassRepository repo, Support support) {
            super(handler, repo, support, GatePass.class);
        }
    }

    @Component
    static class GatePassHandler implements DocumentHandler<GatePass> {
        private final GatePassRepository repo;
        GatePassHandler(GatePassRepository repo) { this.repo = repo; }
        @Override public String docType() { return "GP"; }
        @Override public String periodModule() { return "GA"; }
        @Override public GatePass load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public GatePass save(GatePass doc) { return repo.save(doc); }
        @Override public String summary(GatePass doc) { return doc.getPersonName() != null ? doc.getPersonName() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/ga/incidents")
    static class HsseIncidentController extends DocumentApi<HsseIncident> {
        public HsseIncidentController(HsseIncidentHandler handler, HsseIncidentRepository repo, Support support) {
            super(handler, repo, support, HsseIncident.class);
        }
    }

    @Component
    static class HsseIncidentHandler implements DocumentHandler<HsseIncident> {
        private final HsseIncidentRepository repo;
        HsseIncidentHandler(HsseIncidentRepository repo) { this.repo = repo; }
        @Override public String docType() { return "K3"; }
        @Override public String periodModule() { return "GA"; }
        @Override public HsseIncident load(Long id) { return repo.findById(id).orElseThrow(); }
        @Override public HsseIncident save(HsseIncident doc) { return repo.save(doc); }
        @Override public String summary(HsseIncident doc) { return doc.getDescription() != null ? doc.getDescription() : doc.getDocNo(); }
    }

    @RestController
    @RequestMapping("/api/ga/assets")
    static class InventoryAssetController extends MasterController<InventoryAsset> {
        public InventoryAssetController(InventoryAssetRepository repo, PermissionService perm) {
            super(repo, perm, InventoryAsset.class);
        }
        @Override protected String menuCode() { return "GA-02"; }
    }

    @RestController
    @RequestMapping("/api/ga/room-bookings")
    static class RoomBookingController extends MasterController<RoomBooking> {
        public RoomBookingController(RoomBookingRepository repo, PermissionService perm) {
            super(repo, perm, RoomBooking.class);
        }
        @Override protected String menuCode() { return "GA-05"; }
    }

    @RestController
    @RequestMapping("/api/ga/permits")
    static class CompanyPermitController extends MasterController<CompanyPermit> {
        public CompanyPermitController(CompanyPermitRepository repo, PermissionService perm) {
            super(repo, perm, CompanyPermit.class);
        }
        @Override protected String menuCode() { return "GA-09"; }
    }
}
