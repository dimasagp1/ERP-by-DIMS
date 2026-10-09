package id.herbatech.erp.sys;

import id.herbatech.erp.shared.web.MasterRepository;

interface CompanyRepository extends MasterRepository<Company> {
}

interface PlantRepository extends MasterRepository<Plant> {
}

interface DepartmentRepository extends MasterRepository<Department> {
}

interface CostCenterRepository extends MasterRepository<CostCenter> {
}

interface UomRepository extends MasterRepository<Uom> {
}

interface UomConversionRepository extends MasterRepository<UomConversion> {
}

interface ItemRepository extends MasterRepository<Item> {
}

interface PartnerRepository extends MasterRepository<Partner> {
}

interface WarehouseRepository extends MasterRepository<Warehouse> {
}

interface LocationRepository extends MasterRepository<Location> {
}

interface HolidayRepository extends MasterRepository<Holiday> {
}

interface ShiftRepository extends MasterRepository<Shift> {
}

interface CurrencyRepository extends MasterRepository<Currency> {
}

interface ExchangeRateRepository extends MasterRepository<ExchangeRate> {
}

interface TaxCodeRepository extends MasterRepository<TaxCode> {
}

interface NotificationTemplateRepository extends MasterRepository<NotificationTemplate> {
}
