package id.herbatech.erp.pre;

import id.herbatech.erp.shared.web.MasterRepository;

interface ProductionLineRepository extends MasterRepository<ProductionLine> {
}

interface ProductParamRepository extends MasterRepository<ProductParam> {
}

interface RejectReasonRepository extends MasterRepository<RejectReason> {
}

interface LaborEntryRepository extends MasterRepository<LaborEntry> {
}
