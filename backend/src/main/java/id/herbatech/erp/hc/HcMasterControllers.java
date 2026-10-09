package id.herbatech.erp.hc;

import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.web.LookupSource;
import id.herbatech.erp.shared.web.MasterController;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class HcMasterControllers {

    private HcMasterControllers() {
    }

    /** HC-02 Struktur organisasi & posisi. */
    @RestController
    @RequestMapping("/api/hc/positions")
    static class PositionController extends MasterController<Position> {
        PositionController(PositionRepository repo, PermissionService perm) {
            super(repo, perm, Position.class);
        }

        @Override
        protected String menuCode() {
            return "HC-02";
        }

        @Override
        protected List<String> searchFields() {
            return List.of("code", "title");
        }

        @Override
        protected String[] immutableFields() {
            return new String[]{"code"};
        }

        @Override
        protected void beforeSave(Position p, Position existing) {
            if (existing != null && Objects.equals(p.getReportsToId(), existing.getId())) {
                throw new BusinessException("ORG_CYCLE", "Posisi tidak boleh melapor ke dirinya sendiri");
            }
        }
    }

    /** HC-03 Data karyawan. */
    @RestController
    @RequestMapping("/api/hc/employees")
    static class EmployeeController extends MasterController<Employee> {
        EmployeeController(EmployeeRepository repo, PermissionService perm) {
            super(repo, perm, Employee.class);
        }

        @Override
        protected String menuCode() {
            return "HC-03";
        }

        @Override
        protected List<String> searchFields() {
            return List.of("nik", "name", "email");
        }

        @Override
        protected Sort defaultSort() {
            return Sort.by("nik");
        }

        @Override
        protected String[] immutableFields() {
            return new String[]{"nik"};
        }

        @Override
        protected void beforeSave(Employee e, Employee existing) {
            if (e.getEndDate() != null && e.getEndDate().isBefore(e.getJoinDate())) {
                throw new BusinessException("DATE", "Tanggal keluar tidak boleh sebelum tanggal masuk");
            }
            if (existing != null && Objects.equals(e.getDelegateId(), existing.getId())) {
                throw new BusinessException("DELEGATE", "Karyawan tidak bisa menjadi pengganti dirinya sendiri");
            }
        }
    }

    @Component
    static class HcLookups implements LookupSource {
        @Override
        public Map<String, Def> lookups() {
            return Map.of(
                    "positions", new Def("hc.position", "code", "title", null, "active", Set.of("department_id")),
                    "employees", new Def("hc.employee", "nik", "name", null, "active AND status = 'ACTIVE'",
                            Set.of("department_id", "plant_id")));
        }
    }
}
