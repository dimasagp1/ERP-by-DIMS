package id.herbatech.erp.sys;

import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.web.MasterController;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/** Controller master data SYS-01 s.d. SYS-15 (pola List + Form ringkas). */
final class SysMasterControllers {

    private static final String[] CODE = {"code"};

    private SysMasterControllers() {
    }

    // ---------------------------------------------------------------- SYS-01 Perusahaan, Plant & Site

    @RestController
    @RequestMapping("/api/sys/companies")
    static class CompanyController extends MasterController<Company> {
        CompanyController(CompanyRepository r, PermissionService p) {
            super(r, p, Company.class);
        }

        @Override protected String menuCode() { return "SYS-01"; }
        @Override protected List<String> ownerMenus() { return List.of("FIN-02"); }
        @Override protected String[] immutableFields() { return CODE; }
    }

    @RestController
    @RequestMapping("/api/sys/plants")
    static class PlantController extends MasterController<Plant> {
        PlantController(PlantRepository r, PermissionService p) {
            super(r, p, Plant.class);
        }

        @Override protected String menuCode() { return "SYS-01"; }
        @Override protected List<String> ownerMenus() { return List.of("FIN-02"); }
        @Override protected String[] immutableFields() { return CODE; }
    }

    // ---------------------------------------------------------------- SYS-02 Organisasi & Cost Center

    @RestController
    @RequestMapping("/api/sys/departments")
    static class DepartmentController extends MasterController<Department> {
        DepartmentController(DepartmentRepository r, PermissionService p) {
            super(r, p, Department.class);
        }

        @Override protected String menuCode() { return "SYS-02"; }
        @Override protected List<String> ownerMenus() { return List.of("HC-02"); }
        @Override protected String[] immutableFields() { return CODE; }

        @Override
        protected void beforeSave(Department d, Department existing) {
            if (existing != null && Objects.equals(d.getParentId(), existing.getId())) {
                throw new BusinessException("ORG_CYCLE", "Departemen tidak boleh menjadi induk dirinya sendiri");
            }
        }
    }

    @RestController
    @RequestMapping("/api/sys/cost-centers")
    static class CostCenterController extends MasterController<CostCenter> {
        CostCenterController(CostCenterRepository r, PermissionService p) {
            super(r, p, CostCenter.class);
        }

        @Override protected String menuCode() { return "SYS-02"; }
        @Override protected List<String> ownerMenus() { return List.of("FIN-02"); }
        @Override protected String[] immutableFields() { return CODE; }
    }

    // ---------------------------------------------------------------- SYS-06 Item Master

    @RestController
    @RequestMapping("/api/sys/items")
    static class ItemController extends MasterController<Item> {
        ItemController(ItemRepository r, PermissionService p) {
            super(r, p, Item.class);
        }

        @Override protected String menuCode() { return "SYS-06"; }
        /** Pemilik: Inventory Control. Item baru diajukan RND/PRE/GA lewat RND-11. */
        @Override protected List<String> ownerMenus() { return List.of("SCM-43"); }
        @Override protected String[] immutableFields() { return new String[]{"code", "type"}; }

        @Override
        protected void beforeSave(Item item, Item existing) {
            item.setCode(item.getCode().trim().toUpperCase());
            boolean nonLot = item.getType().equals("ATK") || item.getType().equals("SVC");
            if (nonLot && item.isLotTracked()) {
                throw new BusinessException("ITEM_LOT", "Item ATK dan jasa tidak dilacak per lot");
            }
            if (!nonLot && !item.getType().equals("SP") && !item.isLotTracked()) {
                throw new BusinessException("ITEM_LOT", "Bahan, WIP, dan barang jadi wajib dilacak per lot (PRD SCM aturan 1)");
            }
            if (item.isLotTracked() && (item.getShelfLifeDays() == null || item.getShelfLifeDays() <= 0)
                    && !item.getType().equals("SP")) {
                throw new BusinessException("ITEM_SHELF", "Masa simpan (hari) wajib diisi untuk item yang dilacak per lot");
            }
        }
    }

    // ---------------------------------------------------------------- SYS-07 Satuan & Konversi

    @RestController
    @RequestMapping("/api/sys/uoms")
    static class UomController extends MasterController<Uom> {
        UomController(UomRepository r, PermissionService p) {
            super(r, p, Uom.class);
        }

        @Override protected String menuCode() { return "SYS-07"; }
        @Override protected List<String> ownerMenus() { return List.of("SCM-43"); }
        @Override protected String[] immutableFields() { return CODE; }
    }

    @RestController
    @RequestMapping("/api/sys/uom-conversions")
    static class UomConversionController extends MasterController<UomConversion> {
        UomConversionController(UomConversionRepository r, PermissionService p) {
            super(r, p, UomConversion.class);
        }

        @Override protected String menuCode() { return "SYS-07"; }
        @Override protected List<String> ownerMenus() { return List.of("SCM-43"); }
        @Override protected List<String> searchFields() { return List.of(); }
        @Override protected Sort defaultSort() { return Sort.by("fromUomId"); }

        @Override
        protected void beforeSave(UomConversion c, UomConversion existing) {
            if (Objects.equals(c.getFromUomId(), c.getToUomId())) {
                throw new BusinessException("UOM", "Satuan asal dan tujuan tidak boleh sama");
            }
        }
    }

    // ---------------------------------------------------------------- SYS-08 Mitra Bisnis

    @RestController
    @RequestMapping("/api/sys/partners")
    static class PartnerController extends MasterController<Partner> {
        PartnerController(PartnerRepository r, PermissionService p) {
            super(r, p, Partner.class);
        }

        @Override protected String menuCode() { return "SYS-08"; }
        /** Supplier dimiliki PRC-03, customer dimiliki FIN-22. */
        @Override protected List<String> ownerMenus() { return List.of("PRC-03", "FIN-22"); }
        @Override protected List<String> searchFields() { return List.of("code", "name", "npwp", "city"); }
        @Override protected String[] immutableFields() { return CODE; }

        @Override
        protected void beforeSave(Partner p, Partner existing) {
            if (p.getNpwp() != null && !p.getNpwp().isBlank()) {
                String digits = p.getNpwp().replaceAll("\\D", "");
                if (digits.length() != 15 && digits.length() != 16) {
                    throw new BusinessException("NPWP", "NPWP harus 15 atau 16 digit");
                }
            }
        }
    }

    // ---------------------------------------------------------------- SYS-09 Gudang & Lokasi

    @RestController
    @RequestMapping("/api/sys/warehouses")
    static class WarehouseController extends MasterController<Warehouse> {
        WarehouseController(WarehouseRepository r, PermissionService p) {
            super(r, p, Warehouse.class);
        }

        @Override protected String menuCode() { return "SYS-09"; }
        @Override protected List<String> ownerMenus() { return List.of("SCM-22"); }
        @Override protected String[] immutableFields() { return CODE; }
    }

    @RestController
    @RequestMapping("/api/sys/locations")
    static class LocationController extends MasterController<Location> {
        LocationController(LocationRepository r, PermissionService p) {
            super(r, p, Location.class);
        }

        @Override protected String menuCode() { return "SYS-09"; }
        @Override protected List<String> ownerMenus() { return List.of("SCM-22"); }
        @Override protected List<String> searchFields() { return List.of("binCode", "zone"); }
        @Override protected Sort defaultSort() { return Sort.by("warehouseId", "binCode"); }
    }

    // ---------------------------------------------------------------- SYS-10 Kalender & Shift

    @RestController
    @RequestMapping("/api/sys/holidays")
    static class HolidayController extends MasterController<Holiday> {
        HolidayController(HolidayRepository r, PermissionService p) {
            super(r, p, Holiday.class);
        }

        @Override protected String menuCode() { return "SYS-10"; }
        @Override protected List<String> ownerMenus() { return List.of("HC-07"); }
        @Override protected List<String> searchFields() { return List.of("name"); }
        @Override protected Sort defaultSort() { return Sort.by("date"); }
    }

    @RestController
    @RequestMapping("/api/sys/shifts")
    static class ShiftController extends MasterController<Shift> {
        ShiftController(ShiftRepository r, PermissionService p) {
            super(r, p, Shift.class);
        }

        @Override protected String menuCode() { return "SYS-10"; }
        @Override protected List<String> ownerMenus() { return List.of("HC-07"); }
        @Override protected String[] immutableFields() { return CODE; }
    }

    // ---------------------------------------------------------------- SYS-11 Mata uang & kurs

    @RestController
    @RequestMapping("/api/sys/currencies")
    static class CurrencyController extends MasterController<Currency> {
        CurrencyController(CurrencyRepository r, PermissionService p) {
            super(r, p, Currency.class);
        }

        @Override protected String menuCode() { return "SYS-11"; }
        @Override protected List<String> ownerMenus() { return List.of("FIN-02"); }
        @Override protected String[] immutableFields() { return CODE; }
    }

    @RestController
    @RequestMapping("/api/sys/exchange-rates")
    static class ExchangeRateController extends MasterController<ExchangeRate> {
        ExchangeRateController(ExchangeRateRepository r, PermissionService p) {
            super(r, p, ExchangeRate.class);
        }

        @Override protected String menuCode() { return "SYS-11"; }
        @Override protected List<String> ownerMenus() { return List.of("FIN-02"); }
        @Override protected List<String> searchFields() { return List.of("currencyCode"); }
        @Override protected Sort defaultSort() { return Sort.by("rateDate").descending(); }
    }

    // ---------------------------------------------------------------- SYS-12 Kode pajak

    @RestController
    @RequestMapping("/api/sys/tax-codes")
    static class TaxCodeController extends MasterController<TaxCode> {
        TaxCodeController(TaxCodeRepository r, PermissionService p) {
            super(r, p, TaxCode.class);
        }

        @Override protected String menuCode() { return "SYS-12"; }
        @Override protected List<String> ownerMenus() { return List.of("FIN-60"); }
        @Override protected String[] immutableFields() { return CODE; }
    }

    // ---------------------------------------------------------------- SYS-15 Template & notifikasi

    @RestController
    @RequestMapping("/api/sys/notification-templates")
    static class NotificationTemplateController extends MasterController<NotificationTemplate> {
        NotificationTemplateController(NotificationTemplateRepository r, PermissionService p) {
            super(r, p, NotificationTemplate.class);
        }

        @Override protected String menuCode() { return "SYS-15"; }
        @Override protected String[] immutableFields() { return CODE; }
    }
}
