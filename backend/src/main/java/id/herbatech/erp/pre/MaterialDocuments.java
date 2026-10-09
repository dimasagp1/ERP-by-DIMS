package id.herbatech.erp.pre;

import id.herbatech.erp.fin.FinApi;
import id.herbatech.erp.fin.JournalPostingService;
import id.herbatech.erp.scm.BomQueries;
import id.herbatech.erp.scm.InventoryService;
import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.domain.DocStatus;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

interface MaterialRequestRepository extends DocumentRepository<MaterialRequest> {
}

interface MaterialReturnRepository extends DocumentRepository<MaterialReturn> {
}

/** Jurnal bahan ke/dari barang dalam proses (WIP) per cost center lini. */
@Component
class WipJournal {

    private final FinApi fin;
    private final JournalPostingService journals;
    private final JdbcTemplate jdbc;

    WipJournal(FinApi fin, JournalPostingService journals, JdbcTemplate jdbc) {
        this.fin = fin;
        this.journals = journals;
        this.jdbc = jdbc;
    }

    Long lineCostCenter(Long woId) {
        return jdbc.queryForObject("SELECT l.cost_center_id FROM pre.work_order w JOIN pre.line l ON l.id = w.line_id WHERE w.id = ?", Long.class, woId);
    }

    /** toWip true: Dr WIP, Cr persediaan bahan (serah bahan); false: kebalikannya (retur sisa bahan). */
    void post(Long plantId, LocalDate date, Long woId, Map<String, BigDecimal> byType, boolean toWip, String docType, Long docId, String docNo,
              String desc) {
        List<JournalPostingService.IdLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : byType.entrySet()) {
            BigDecimal v = e.getValue().setScale(2, RoundingMode.HALF_UP);
            if (v.signum() == 0) {
                continue;
            }
            total = total.add(v);
            lines.add(new JournalPostingService.IdLine(fin.inventoryAccount(e.getKey()), null, desc + " · " + e.getKey(), toWip ? null : v, toWip ? v : null));
        }
        if (total.signum() == 0) {
            return;
        }
        lines.add(new JournalPostingService.IdLine(fin.inventoryAccount("WIP"), lineCostCenter(woId), desc, toWip ? total : null, toWip ? null : total));
        journals.postIds(plantId, date, lines, docType, docId, docNo, desc);
    }
}

// =====================================================================================================================
// PRE-04 Permintaan bahan (diserahkan gudang di SCM-23)
// =====================================================================================================================

@Component
class MaterialRequestHandler implements DocumentHandler<MaterialRequest> {

    private final MaterialRequestRepository repo;
    private final WorkOrderRepository workOrders;
    private final JdbcTemplate jdbc;

    MaterialRequestHandler(MaterialRequestRepository repo, WorkOrderRepository workOrders, JdbcTemplate jdbc) {
        this.repo = repo;
        this.workOrders = workOrders;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "MR"; }
    @Override public String periodModule() { return "PRE"; }
    @Override public MaterialRequest load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Permintaan bahan", id)); }
    @Override public MaterialRequest save(MaterialRequest doc) { return repo.save(doc); }

    /** Tidak diposting: gudang menyerahkan bahan (SCM-23) dan dokumen selesai saat terpenuhi. */
    @Override
    public boolean allows(String action, MaterialRequest doc) {
        return !"POST".equals(action) && !"REVERSE".equals(action);
    }

    WorkOrder wo(MaterialRequest d) {
        return workOrders.findById(d.getWoId()).orElseThrow(() -> new NotFoundException("Work order", d.getWoId()));
    }

    @Override
    public String summary(MaterialRequest d) {
        WorkOrder w = wo(d);
        return "%s · batch %s · %d bahan".formatted(w.getDocNo(), w.getBatchNo(), d.getLines().size());
    }

    @Override
    public void validateSubmit(MaterialRequest d) {
        WorkOrder w = wo(d);
        if (w.getStatus() != DocStatus.APPROVED) {
            throw new BusinessException("MR_WO", "Work order " + w.getDocNo() + " belum dirilis atau sudah selesai");
        }
        if (d.getLines().isEmpty()) {
            throw new BusinessException("MR_LINES", "Isi minimal satu bahan (atau isi dari BOM)");
        }
        for (MaterialRequest.Line l : d.getLines()) {
            if (l.getItemId() == null || l.getQty() == null || l.getQty().signum() <= 0) {
                throw new BusinessException("MR_LINE", "Baris " + l.getLineNo() + ": item dan qty wajib diisi");
            }
            if (l.getQtyBom().signum() > 0 && l.getQty().compareTo(l.getQtyBom().multiply(new BigDecimal("1.10"))) > 0
                    && (l.getNote() == null || l.getNote().isBlank())) {
                throw new BusinessException("MR_OVER", "Baris " + l.getLineNo() + ": melebihi kebutuhan BOM > 10%, beri alasan");
            }
        }
        Long open = jdbc.queryForObject("""
                SELECT count(*) FROM pre.material_request WHERE wo_id = ? AND id <> ? AND status IN ('SUBMITTED','APPROVED')""",
                Long.class, d.getWoId(), d.getId());
        if (open != null && open > 0 && (d.getNotes() == null || d.getNotes().isBlank())) {
            throw new BusinessException("MR_DUP", "Sudah ada permintaan bahan terbuka untuk WO ini; jelaskan alasan permintaan tambahan di catatan");
        }
    }
}

@RestController
@RequestMapping("/api/pre/material-requests")
class MaterialRequestController extends DocumentApi<MaterialRequest> {

    private final MaterialRequestHandler handler;
    private final BomQueries boms;
    private final InventoryService inventory;
    private final WipJournal wip;
    private final TimeService time;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;

    MaterialRequestController(MaterialRequestHandler handler, MaterialRequestRepository repo, Support support, BomQueries boms,
                              InventoryService inventory, WipJournal wip, TimeService time, JdbcTemplate jdbc, PermissionService perm) {
        super(handler, repo, support, MaterialRequest.class);
        this.handler = handler;
        this.boms = boms;
        this.inventory = inventory;
        this.wip = wip;
        this.time = time;
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("issuedAt", "issuedBy", "issuedValue");
    }

    @Override
    protected void apply(MaterialRequest target, MaterialRequest in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (MaterialRequest.Line l : in.getLines()) {
            l.setId(null);
            l.setRequest(target);
            l.setLineNo(no++);
            l.setQtyIssued(BigDecimal.ZERO);
            if (l.getQtyBom() == null) {
                l.setQtyBom(BigDecimal.ZERO);
            }
            target.getLines().add(l);
        }
    }

    /** Draft tanpa baris otomatis diisi dari BOM berlaku × qty rencana WO. */
    @Override
    protected void beforeSave(MaterialRequest d, boolean isNew) {
        require(d.getWoId() != null, "MR_WO", "Pilih work order");
        if (d.getLines().isEmpty()) {
            fillFromBom(d);
        }
        if (d.getNeededAt() == null) {
            d.setNeededAt(handler.wo(d).getPlannedStart());
        }
    }

    void fillFromBom(MaterialRequest d) {
        WorkOrder w = handler.wo(d);
        List<BomQueries.Requirement> req = boms.explode(w.getPlantId(), w.getProductItemId(), w.getQtyPlan());
        require(!req.isEmpty(), "MR_BOM", "Produk ini belum punya BOM berlaku (SCM-05)");
        d.getLines().clear();
        short no = 1;
        for (BomQueries.Requirement r : req) {
            MaterialRequest.Line l = new MaterialRequest.Line();
            l.setRequest(d);
            l.setLineNo(no++);
            l.setItemId(r.itemId());
            l.setQtyBom(r.qty());
            l.setQty(r.qty());
            d.getLines().add(l);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, MaterialRequest d) {
        WorkOrder w = handler.wo(d);
        body.put("woNo", w.getDocNo());
        body.put("batchNo", w.getBatchNo());
        body.put("productName", jdbc.queryForObject("SELECT name FROM sys.item WHERE id = ?", String.class, w.getProductItemId()));
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                InventoryService.ItemInfo i = inventory.item(((Number) m.get("itemId")).longValue());
                m.put("itemLabel", i.code() + " · " + i.name());
                m.put("uom", i.uom());
            }
        }
    }

    @PostMapping("/{id}/fill-bom")
    @Transactional
    public Envelope refill(@PathVariable Long id) {
        MaterialRequest d = handler.load(id);
        workflow().assertEditable(handler, d);
        fillFromBom(d);
        handler.save(d);
        workflow().touched(handler, d);
        return envelope(handler.load(id));
    }

    // ------------------------------------------------------------------ SCM-23 Picking & serah bahan (gudang)

    @GetMapping("/to-issue")
    public List<Map<String, Object>> toIssue() {
        perm.require("SCM-23", Action.VIEW);
        return jdbc.queryForList("""
                SELECT r.id, r.doc_no, r.doc_date, r.needed_at, w.doc_no AS wo_no, w.batch_no, i.name AS product, l.code AS line_code,
                       (SELECT COUNT(*) FROM pre.material_request_line x WHERE x.request_id = r.id AND x.qty_issued < x.qty) AS open_lines
                FROM pre.material_request r JOIN pre.work_order w ON w.id = r.wo_id JOIN sys.item i ON i.id = w.product_item_id
                JOIN pre.line l ON l.id = w.line_id WHERE r.plant_id = ? AND r.status = 'APPROVED' ORDER BY r.needed_at, r.doc_no""",
                UserContext.plantId());
    }

    record Pick(Long lineId, Long itemId, String itemLabel, String uom, Long lotId, String lotNo, LocalDate expDate, Long locationId,
                String binCode, BigDecimal qty, String shortage) {
    }

    /** Saran pick FEFO dari gudang sesuai jenis bahan untuk sisa permintaan (SCM aturan 2). */
    @GetMapping("/{id}/picks")
    public List<Pick> picks(@PathVariable Long id) {
        perm.require("SCM-23", Action.VIEW);
        MaterialRequest d = handler.load(id);
        List<Pick> out = new ArrayList<>();
        for (MaterialRequest.Line l : d.getLines()) {
            BigDecimal remaining = l.getQty().subtract(l.getQtyIssued());
            if (remaining.signum() <= 0) {
                continue;
            }
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            Long wh = jdbc.queryForList("SELECT id FROM sys.warehouse WHERE plant_id = ? AND type = ? AND active ORDER BY id LIMIT 1", Long.class,
                    d.getPlantId(), i.type()).stream().findFirst().orElse(null);
            try {
                if (wh == null) {
                    throw new BusinessException("NO_WH", "Tidak ada gudang tipe " + i.type());
                }
                for (InventoryService.FefoPick p : inventory.suggestFefo(l.getItemId(), remaining, wh, time.today())) {
                    out.add(new Pick(l.getId(), l.getItemId(), i.code() + " · " + i.name(), i.uom(), p.lotId(), p.lotNo(), p.expDate(),
                            p.locationId(), p.binCode(), p.qty(), null));
                }
            } catch (BusinessException e) {
                out.add(new Pick(l.getId(), l.getItemId(), i.code() + " · " + i.name(), i.uom(), null, null, null, null, null, remaining, e.getMessage()));
            }
        }
        return out;
    }

    record IssueLine(Long lineId, Long lotId, Long locationId, BigDecimal qty) {
    }

    record IssueRequest(List<IssueLine> picks) {
    }

    /** Serahkan bahan ke produksi: stok keluar per lot (Released & belum kedaluwarsa), biaya masuk WIP batch. */
    @PostMapping("/{id}/issue")
    @Transactional
    public Envelope issue(@PathVariable Long id, @RequestBody IssueRequest req) {
        perm.require("SCM-23", Action.CREATE);
        MaterialRequest d = handler.load(id);
        require(d.getStatus() == DocStatus.APPROVED, "MR_STATE", "Permintaan belum disetujui atau sudah selesai");
        require(req.picks() != null && !req.picks().isEmpty(), "MR_PICKS", "Tidak ada bahan yang diserahkan");
        WorkOrder w = handler.wo(d);
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        BigDecimal value = BigDecimal.ZERO;
        LocalDate today = time.today();
        for (IssueLine p : req.picks()) {
            MaterialRequest.Line l = d.getLines().stream().filter(x -> x.getId().equals(p.lineId())).findFirst()
                    .orElseThrow(() -> new BusinessException("MR_LINE", "Baris permintaan tidak ditemukan"));
            require(p.qty() != null && p.qty().signum() > 0, "MR_QTY", "Qty serah harus lebih dari nol");
            require(l.getQtyIssued().add(p.qty()).compareTo(l.getQty().multiply(new BigDecimal("1.02"))) <= 0, "MR_OVER",
                    "Qty serah melebihi permintaan baris " + l.getLineNo());
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            BigDecimal cost = inventory.unitCost(l.getItemId(), p.lotId(), p.locationId());
            inventory.move(new InventoryService.MoveCommand(InventoryService.MoveType.ISSUE, l.getItemId(), p.lotId(), p.locationId(), null,
                    p.qty(), cost, today, "MR", d.getId(), d.getDocNo(), "Serah bahan " + w.getDocNo() + " batch " + w.getBatchNo()));
            l.setQtyIssued(l.getQtyIssued().add(p.qty()));
            BigDecimal v = p.qty().multiply(cost).setScale(2, RoundingMode.HALF_UP);
            byType.merge(i.type(), v, BigDecimal::add);
            value = value.add(v);
        }
        wip.post(d.getPlantId(), today, d.getWoId(), byType, true, "MR", d.getId(), d.getDocNo(), "Bahan ke produksi " + w.getBatchNo());
        d.setIssuedAt(time.now());
        d.setIssuedBy(UserContext.userId());
        d.setIssuedValue(d.getIssuedValue().add(value));
        handler.save(d);
        if (d.getLines().stream().allMatch(x -> x.getQtyIssued().compareTo(x.getQty()) >= 0)) {
            workflow().markDone("MR", id, "Bahan diserahkan lengkap oleh gudang");
        }
        return envelope(handler.load(id));
    }
}

// =====================================================================================================================
// PRE-10 Retur sisa bahan (diterima gudang di SCM-23)
// =====================================================================================================================

@Component
class MaterialReturnHandler implements DocumentHandler<MaterialReturn> {

    private final MaterialReturnRepository repo;
    private final WorkOrderRepository workOrders;
    private final JdbcTemplate jdbc;

    MaterialReturnHandler(MaterialReturnRepository repo, WorkOrderRepository workOrders, JdbcTemplate jdbc) {
        this.repo = repo;
        this.workOrders = workOrders;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "MRT"; }
    @Override public String periodModule() { return "PRE"; }
    @Override public MaterialReturn load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Retur bahan", id)); }
    @Override public MaterialReturn save(MaterialReturn doc) { return repo.save(doc); }

    @Override
    public boolean allows(String action, MaterialReturn doc) {
        return !"POST".equals(action) && !"REVERSE".equals(action);
    }

    WorkOrder wo(MaterialReturn d) {
        return workOrders.findById(d.getWoId()).orElseThrow(() -> new NotFoundException("Work order", d.getWoId()));
    }

    @Override
    public String summary(MaterialReturn d) {
        WorkOrder w = wo(d);
        return "%s · batch %s · %d bahan".formatted(w.getDocNo(), w.getBatchNo(), d.getLines().size());
    }

    /** Bersih diserahkan ke WO untuk lot ini (serah − retur lain). */
    BigDecimal netIssued(Long woId, Long itemId, Long lotId, Long excludeReturnId) {
        return jdbc.queryForObject("""
                SELECT COALESCE((SELECT SUM(m.qty) FROM scm.stock_move m JOIN pre.material_request r ON m.ref_doc_type = 'MR' AND m.ref_doc_id = r.id
                                 WHERE r.wo_id = ? AND m.item_id = ? AND COALESCE(m.lot_id, 0) = COALESCE(?::bigint, 0)), 0)
                     - COALESCE((SELECT SUM(l.qty) FROM pre.material_return_line l JOIN pre.material_return x ON x.id = l.return_id
                                 WHERE x.wo_id = ? AND x.id <> ? AND x.status NOT IN ('CANCELLED','REJECTED') AND l.item_id = ?
                                   AND COALESCE(l.lot_id, 0) = COALESCE(?::bigint, 0)), 0)""",
                BigDecimal.class, woId, itemId, lotId, woId, excludeReturnId == null ? -1L : excludeReturnId, itemId, lotId);
    }

    @Override
    public void validateSubmit(MaterialReturn d) {
        if (d.getLines().isEmpty()) {
            throw new BusinessException("MRT_LINES", "Isi minimal satu baris");
        }
        for (MaterialReturn.Line l : d.getLines()) {
            BigDecimal issued = netIssued(d.getWoId(), l.getItemId(), l.getLotId(), d.getId());
            if (l.getQty() == null || l.getQty().signum() <= 0 || l.getQty().compareTo(issued) > 0) {
                throw BusinessException.of("MRT_QTY", "Baris %d: retur melebihi bahan yang diserahkan ke WO untuk lot ini (%s)", l.getLineNo(),
                        issued.stripTrailingZeros().toPlainString());
            }
        }
    }
}

@RestController
@RequestMapping("/api/pre/material-returns")
class MaterialReturnController extends DocumentApi<MaterialReturn> {

    private final MaterialReturnHandler handler;
    private final InventoryService inventory;
    private final WipJournal wip;
    private final TimeService time;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;

    MaterialReturnController(MaterialReturnHandler handler, MaterialReturnRepository repo, Support support, InventoryService inventory,
                             WipJournal wip, TimeService time, JdbcTemplate jdbc, PermissionService perm) {
        super(handler, repo, support, MaterialReturn.class);
        this.handler = handler;
        this.inventory = inventory;
        this.wip = wip;
        this.time = time;
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("receivedAt", "receivedBy", "returnedValue");
    }

    @Override
    protected void apply(MaterialReturn target, MaterialReturn in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (MaterialReturn.Line l : in.getLines()) {
            l.setId(null);
            l.setMaterialReturn(target);
            l.setLineNo(no++);
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(MaterialReturn d, boolean isNew) {
        require(d.getWoId() != null, "MRT_WO", "Pilih work order");
        for (MaterialReturn.Line l : d.getLines()) {
            require(l.getItemId() != null && l.getQty() != null, "MRT_LINE", "Baris " + l.getLineNo() + ": item & qty wajib diisi");
            l.setUnitCost(jdbc.queryForObject("""
                    SELECT COALESCE(SUM(m.qty * m.unit_cost) / NULLIF(SUM(m.qty), 0), 0) FROM scm.stock_move m
                    JOIN pre.material_request r ON m.ref_doc_type = 'MR' AND m.ref_doc_id = r.id
                    WHERE r.wo_id = ? AND m.item_id = ? AND COALESCE(m.lot_id, 0) = COALESCE(?::bigint, 0)""",
                    BigDecimal.class, d.getWoId(), l.getItemId(), l.getLotId()));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, MaterialReturn d) {
        WorkOrder w = handler.wo(d);
        body.put("woNo", w.getDocNo());
        body.put("batchNo", w.getBatchNo());
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                InventoryService.ItemInfo i = inventory.item(((Number) m.get("itemId")).longValue());
                m.put("itemLabel", i.code() + " · " + i.name());
                m.put("uom", i.uom());
                m.put("lotNo", m.get("lotId") == null ? null : jdbc.queryForObject("SELECT lot_no FROM scm.lot WHERE id = ?", String.class,
                        ((Number) m.get("lotId")).longValue()));
            }
        }
    }

    @GetMapping("/to-receive")
    public List<Map<String, Object>> toReceive() {
        perm.require("SCM-23", Action.VIEW);
        return jdbc.queryForList("""
                SELECT r.id, r.doc_no, r.doc_date, w.doc_no AS wo_no, w.batch_no, COUNT(l.id) AS lines
                FROM pre.material_return r JOIN pre.work_order w ON w.id = r.wo_id JOIN pre.material_return_line l ON l.return_id = r.id
                WHERE r.plant_id = ? AND r.status = 'APPROVED' GROUP BY r.id, w.doc_no, w.batch_no ORDER BY r.doc_date""", UserContext.plantId());
    }

    record ReceiveLine(Long lineId, Long locationId) {
    }

    /** Gudang menerima sisa bahan ke lokasi simpan; biaya bahan batch berkurang (WIP → persediaan). */
    @PostMapping("/{id}/receive")
    @Transactional
    public Envelope receive(@PathVariable Long id, @RequestBody List<ReceiveLine> locations) {
        perm.require("SCM-23", Action.CREATE);
        MaterialReturn d = handler.load(id);
        require(d.getStatus() == DocStatus.APPROVED, "MRT_STATE", "Retur belum disetujui atau sudah diterima");
        handler.validateSubmit(d);
        WorkOrder w = handler.wo(d);
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        BigDecimal value = BigDecimal.ZERO;
        LocalDate today = time.today();
        for (MaterialReturn.Line l : d.getLines()) {
            Long loc = locations == null ? null : locations.stream().filter(x -> x.lineId().equals(l.getId())).map(ReceiveLine::locationId)
                    .findFirst().orElse(null);
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            l.setLocationId(loc != null ? loc : inventory.defaultLocation(d.getPlantId(), i.type()));
            inventory.move(new InventoryService.MoveCommand(InventoryService.MoveType.RETURN, l.getItemId(), l.getLotId(), null, l.getLocationId(),
                    l.getQty(), l.getUnitCost(), today, "MRT", d.getId(), d.getDocNo(), "Retur sisa bahan " + w.getBatchNo()));
            BigDecimal v = l.getQty().multiply(l.getUnitCost()).setScale(2, RoundingMode.HALF_UP);
            byType.merge(i.type(), v, BigDecimal::add);
            value = value.add(v);
        }
        wip.post(d.getPlantId(), today, d.getWoId(), byType, false, "MRT", d.getId(), d.getDocNo(), "Retur sisa bahan " + w.getBatchNo());
        d.setReceivedAt(time.now());
        d.setReceivedBy(UserContext.userId());
        d.setReturnedValue(value);
        handler.save(d);
        workflow().markDone("MRT", id, "Diterima gudang");
        return envelope(handler.load(id));
    }
}
