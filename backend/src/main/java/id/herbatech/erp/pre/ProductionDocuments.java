package id.herbatech.erp.pre;

import id.herbatech.erp.fin.FinApi;
import id.herbatech.erp.fin.JournalPostingService;
import id.herbatech.erp.hc.HcQueries;
import id.herbatech.erp.scm.InventoryService;
import id.herbatech.erp.scm.Lot;
import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.document.DocumentWorkflowService;
import id.herbatech.erp.shared.document.NumberingService;
import id.herbatech.erp.shared.domain.DocStatus;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import org.springframework.context.annotation.Lazy;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

interface WorkOrderRepository extends DocumentRepository<WorkOrder> {
}

interface ProductionOutputRepository extends DocumentRepository<ProductionOutput> {
}

// =====================================================================================================================
// PRE-02 Work Order
// =====================================================================================================================

@Component
class WorkOrderHandler implements DocumentHandler<WorkOrder> {

    private final WorkOrderRepository repo;
    private final HcQueries hc;
    private final NumberingService numbering;
    private final JdbcTemplate jdbc;

    WorkOrderHandler(WorkOrderRepository repo, HcQueries hc, NumberingService numbering, JdbcTemplate jdbc) {
        this.repo = repo;
        this.hc = hc;
        this.numbering = numbering;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "WO"; }
    @Override public String periodModule() { return "PRE"; }
    @Override public WorkOrder load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Work order", id)); }
    @Override public WorkOrder save(WorkOrder doc) { return repo.save(doc); }

    @Override
    public String summary(WorkOrder d) {
        Map<String, Object> m = jdbc.queryForMap("""
                SELECT i.name AS item, l.name AS line FROM sys.item i, pre.line l WHERE i.id = ? AND l.id = ?""",
                d.getProductItemId(), d.getLineId());
        return "%s · %s · %s%s".formatted(m.get("item"), m.get("line"), d.getQtyPlan().stripTrailingZeros().toPlainString(),
                d.getBatchNo() == null ? "" : " · batch " + d.getBatchNo());
    }

    /** WO tidak diposting; selesai otomatis saat hasil produksi diposting. */
    @Override
    public boolean allows(String action, WorkOrder doc) {
        return !"POST".equals(action) && !"REVERSE".equals(action);
    }

    @Override
    public void validateSubmit(WorkOrder d) {
        if (d.getOperators().isEmpty()) {
            throw new BusinessException("WO_OPS", "Tetapkan minimal satu operator");
        }
        checkOperators(d, d.getPlannedStart());
    }

    /** PRE aturan 1: semua operator wajib berkualifikasi aktif (HC-12) untuk proses lini. */
    void checkOperators(WorkOrder d, LocalDate date) {
        Set<Long> seen = new HashSet<>();
        List<String> problems = new ArrayList<>();
        for (WorkOrder.Operator o : d.getOperators()) {
            if (!seen.add(o.getEmployeeId())) {
                throw new BusinessException("WO_DUP", "Operator " + hc.employeeName(o.getEmployeeId()) + " tercantum dua kali");
            }
            hc.requireActive(o.getEmployeeId());
            if (!hc.isQualified(o.getEmployeeId(), o.getProcessCode(), date)) {
                problems.add(hc.employeeName(o.getEmployeeId()) + " (" + o.getProcessCode() + ")");
            }
        }
        if (!problems.isEmpty()) {
            throw new BusinessException("WO_QUALIFICATION", "Operator belum berkualifikasi aktif per " + date + ": "
                    + String.join(", ", problems) + ". Perbarui di HC-12.");
        }
    }

    /** Rilis ke lini: terbitkan nomor batch [kode produk][YY][MM][urut] dan tanggal kedaluwarsa. */
    @Override
    public void onApproved(WorkOrder d) {
        Map<String, Object> p = jdbc.queryForMap("""
                SELECT pp.batch_prefix, i.shelf_life_days FROM pre.product_param pp JOIN sys.item i ON i.id = pp.item_id
                WHERE pp.item_id = ?""", d.getProductItemId());
        if (d.getBatchNo() == null) {
            d.setBatchNo(numbering.nextBatchNo((String) p.get("batch_prefix"), d.getPlannedStart()));
        }
        d.setMfgDate(d.getPlannedStart());
        Integer shelf = (Integer) p.get("shelf_life_days");
        d.setExpDate(shelf == null ? null : d.getPlannedStart().plusDays(shelf));
    }
}

@RestController
@RequestMapping("/api/pre/work-orders")
class WorkOrderController extends DocumentApi<WorkOrder> {

    private final WorkOrderHandler wo;
    private final HcQueries hc;
    private final PermissionService perm;
    private final TimeService time;
    private final JdbcTemplate jdbc;

    WorkOrderController(WorkOrderHandler handler, WorkOrderRepository repo, Support support, HcQueries hc, PermissionService perm,
                        TimeService time, JdbcTemplate jdbc) {
        super(handler, repo, support, WorkOrder.class);
        this.wo = handler;
        this.hc = hc;
        this.perm = perm;
        this.time = time;
        this.jdbc = jdbc;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "batchNo", "notes");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("batchNo", "mfgDate", "expDate", "startedAt", "finishedAt", "qtyGood", "qtyReject", "yieldPct", "source");
    }

    @Override
    protected void apply(WorkOrder target, WorkOrder in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getOperators().clear();
        short no = 1;
        for (WorkOrder.Operator o : in.getOperators()) {
            o.setId(null);
            o.setWorkOrder(target);
            o.setLineNo(no++);
            target.getOperators().add(o);
        }
    }

    @Override
    protected void beforeSave(WorkOrder d, boolean isNew) {
        require(d.getProductItemId() != null, "WO_ITEM", "Produk wajib dipilih");
        String type = jdbc.queryForList("SELECT type FROM sys.item WHERE id = ? AND active", String.class, d.getProductItemId())
                .stream().findFirst().orElseThrow(() -> new BusinessException("WO_ITEM", "Produk tidak ditemukan"));
        require("FG".equals(type) || "WIP".equals(type), "WO_ITEM", "Work order hanya untuk barang jadi atau WIP");
        Long params = jdbc.queryForObject("SELECT count(*) FROM pre.product_param WHERE item_id = ? AND active", Long.class, d.getProductItemId());
        require(params != null && params > 0, "WO_PARAM", "Parameter produksi produk ini belum diatur (PRE-99)");
        require(d.getQtyPlan() != null && d.getQtyPlan().signum() > 0, "WO_QTY", "Jumlah rencana harus lebih dari nol");
        require(d.getLineId() != null, "WO_LINE", "Lini wajib dipilih");
        require(d.getPlannedStart() != null, "WO_DATE", "Tanggal mulai rencana wajib diisi");
        if (d.getPlannedEnd() == null) {
            d.setPlannedEnd(d.getPlannedStart());
        }
        require(!d.getPlannedEnd().isBefore(d.getPlannedStart()), "WO_DATE", "Tanggal selesai tidak boleh sebelum mulai");
        String process = jdbc.queryForObject("SELECT process_code FROM pre.line WHERE id = ?", String.class, d.getLineId());
        for (WorkOrder.Operator o : d.getOperators()) {
            require(o.getEmployeeId() != null, "WO_OP", "Pilih karyawan di setiap baris operator");
            if (o.getProcessCode() == null || o.getProcessCode().isBlank()) {
                o.setProcessCode(process);
            }
            if (o.getRole() == null) {
                o.setRole("OPERATOR");
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, WorkOrder d) {
        jdbc.query("""
                SELECT i.code, i.name, u.code AS uom, l.code AS line_code, l.name AS line_name, pp.min_yield_pct
                FROM sys.item i JOIN sys.uom u ON u.id = i.uom_id JOIN pre.line l ON l.id = ?
                LEFT JOIN pre.product_param pp ON pp.item_id = i.id WHERE i.id = ?""", rs -> {
            body.put("productCode", rs.getString("code"));
            body.put("productName", rs.getString("name"));
            body.put("uom", rs.getString("uom"));
            body.put("lineName", rs.getString("line_code") + " · " + rs.getString("line_name"));
            body.put("minYieldPct", rs.getBigDecimal("min_yield_pct"));
        }, d.getLineId(), d.getProductItemId());
        body.put("started", d.getStartedAt() != null);
        if (body.get("operators") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                Long emp = ((Number) m.get("employeeId")).longValue();
                m.put("employeeName", hc.employeeName(emp));
                m.put("qualified", hc.isQualified(emp, (String) m.get("processCode"), d.getPlannedStart()));
            }
        }
    }

    record FromPlan(Long itemId, BigDecimal qty, LocalDate plannedStart, Long lineId, String notes) {
    }

    /**
     * SCM-07 Rilis WO oleh PPIC dari MPS: draft WO dibuat sistem (lini default produk); Manager Produksi menetapkan
     * operator berkualifikasi lalu mengajukan rilis (PRE-02).
     */
    @PostMapping("/from-plan")
    @Transactional
    public Envelope fromPlan(@RequestBody FromPlan req) {
        perm.require("SCM-07", Action.CREATE);
        WorkOrder w = new WorkOrder();
        w.setProductItemId(req.itemId());
        w.setQtyPlan(req.qty());
        w.setPlannedStart(req.plannedStart() == null ? time.today() : req.plannedStart());
        w.setDocDate(time.today());
        w.setLineId(req.lineId() != null ? req.lineId() : jdbc.queryForList("SELECT default_line_id FROM pre.product_param WHERE item_id = ?",
                Long.class, req.itemId()).stream().filter(java.util.Objects::nonNull).findFirst()
                .orElseThrow(() -> new BusinessException("WO_LINE", "Produk belum punya lini default (PRE-99); pilih lini")));
        w.setSource("PPIC");
        w.setNotes(req.notes() == null ? "Rilis PPIC dari MPS" : req.notes());
        beforeSave(w, true);
        WorkOrder saved = workflow().initSystemDraft(wo, w, UserContext.plantId(),
                "Dibuat PPIC (SCM-07) dari MPS; menunggu penetapan operator oleh Produksi");
        return envelope(saved);
    }

    /** SCM-07: rencana MPS vs WO yang sudah diterbitkan per produk per minggu. */
    @GetMapping("/plan-board")
    public Map<String, Object> planBoard() {
        perm.require("SCM-07", Action.VIEW);
        Long plant = UserContext.plantId();
        LocalDate from = time.today().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        List<Map<String, Object>> mps = jdbc.queryForList("""
                SELECT m.item_id, i.code, i.name, m.week_start, m.qty, m.firm,
                       COALESCE((SELECT SUM(w.qty_plan) FROM pre.work_order w WHERE w.product_item_id = m.item_id AND w.plant_id = m.plant_id
                                 AND w.status <> 'CANCELLED' AND w.planned_start >= m.week_start AND w.planned_start < m.week_start + 7), 0) AS released
                FROM scm.mps m JOIN sys.item i ON i.id = m.item_id
                WHERE m.plant_id = ? AND m.week_start >= ? AND m.qty > 0 ORDER BY m.week_start, i.code""", plant, java.sql.Date.valueOf(from));
        List<Map<String, Object>> wos = jdbc.queryForList("""
                SELECT w.id, w.doc_no, w.status, w.batch_no, w.exp_date, w.planned_start, w.qty_plan, w.source, i.code AS item_code, i.name AS item_name,
                       l.code AS line_code
                FROM pre.work_order w JOIN sys.item i ON i.id = w.product_item_id JOIN pre.line l ON l.id = w.line_id
                WHERE w.plant_id = ? AND w.planned_start >= ? - 30 ORDER BY w.planned_start DESC LIMIT 200""", plant, java.sql.Date.valueOf(from));
        return Map.of("mps", mps, "workOrders", wos);
    }

    /** Mulai produksi: WO harus sudah dirilis; kualifikasi operator dicek ulang pada hari mulai. */
    @PostMapping("/{id}/start")
    @Transactional
    public Envelope start(@PathVariable Long id) {
        perm.require("PRE-02", Action.EDIT);
        WorkOrder d = wo.load(id);
        if (d.getStatus() != DocStatus.APPROVED) {
            throw new BusinessException("WO_STATE", "Work order harus sudah dirilis (disetujui) sebelum dimulai");
        }
        if (d.getStartedAt() != null) {
            throw new BusinessException("WO_STARTED", "Work order sudah dimulai");
        }
        wo.checkOperators(d, time.today());
        d.setStartedAt(time.now());
        wo.save(d);
        return envelope(d);
    }
}

// =====================================================================================================================
// PRE-08 Hasil produksi
// =====================================================================================================================

@Component
class ProductionOutputHandler implements DocumentHandler<ProductionOutput> {

    private final ProductionOutputRepository repo;
    private final WorkOrderRepository workOrders;
    private final InventoryService inventory;
    private final DocumentWorkflowService workflow;
    private final TimeService time;
    private final JdbcTemplate jdbc;
    private final FinApi fin;
    private final JournalPostingService journals;
    private final WipJournal wip;

    ProductionOutputHandler(ProductionOutputRepository repo, WorkOrderRepository workOrders, InventoryService inventory,
                            @Lazy DocumentWorkflowService workflow, TimeService time, JdbcTemplate jdbc, FinApi fin,
                            JournalPostingService journals, WipJournal wip) {
        this.fin = fin;
        this.journals = journals;
        this.wip = wip;
        this.repo = repo;
        this.workOrders = workOrders;
        this.inventory = inventory;
        this.workflow = workflow;
        this.time = time;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "HP"; }
    @Override public String periodModule() { return "PRE"; }
    @Override public ProductionOutput load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Hasil produksi", id)); }
    @Override public ProductionOutput save(ProductionOutput doc) { return repo.save(doc); }

    WorkOrder wo(ProductionOutput d) {
        return workOrders.findById(d.getWoId()).orElseThrow(() -> new NotFoundException("Work order", d.getWoId()));
    }

    @Override
    public String summary(ProductionOutput d) {
        WorkOrder w = wo(d);
        return "Hasil %s · batch %s · %s baik, %s reject".formatted(w.getDocNo(), w.getBatchNo(),
                d.getQtyGood().stripTrailingZeros().toPlainString(), d.getQtyReject().stripTrailingZeros().toPlainString());
    }

    @Override
    public void validateSubmit(ProductionOutput d) {
        WorkOrder w = wo(d);
        if (w.getStatus() != DocStatus.APPROVED || w.getStartedAt() == null) {
            throw new BusinessException("HP_WO", "Work order " + w.getDocNo() + " belum dimulai atau sudah selesai");
        }
        Long other = jdbc.queryForObject("""
                SELECT count(*) FROM pre.production_output WHERE wo_id = ? AND id <> ? AND status IN ('SUBMITTED','APPROVED','POSTED')""",
                Long.class, d.getWoId(), d.getId());
        if (other != null && other > 0) {
            throw new BusinessException("HP_DUP", "Hasil produksi untuk WO ini sudah dilaporkan");
        }
        BigDecimal minYield = jdbc.queryForObject("SELECT min_yield_pct FROM pre.product_param WHERE item_id = ?", BigDecimal.class,
                w.getProductItemId());
        if (d.getYieldPct().compareTo(minYield) < 0 && (d.getYieldExplanation() == null || d.getYieldExplanation().trim().length() < 10)) {
            throw BusinessException.of("HP_YIELD", "Yield %s%% di bawah minimum %s%%: wajib diberi penjelasan (PRE aturan 5)",
                    d.getYieldPct().toPlainString(), minYield.stripTrailingZeros().toPlainString());
        }
        if (d.getQtyReject().signum() > 0 && d.getRejectReasonId() == null) {
            throw new BusinessException("HP_REJECT", "Pilih alasan reject");
        }
        Boolean quarantine = jdbc.queryForList("SELECT is_quarantine FROM sys.location WHERE id = ?", Boolean.class, d.getToLocationId())
                .stream().findFirst().orElse(false);
        if (!quarantine) {
            throw new BusinessException("HP_LOC", "Barang jadi wajib diserahkan ke lokasi karantina (PRE aturan 6)");
        }
    }

    /**
     * Nilai barang jadi: biaya standar berlaku (FIN-52) × qty baik; tanpa standar memakai biaya bahan bersih yang sudah
     * diserahkan ke batch. Selisih terhadap biaya aktual ditutup di FIN-53.
     */
    BigDecimal unitValue(WorkOrder w, BigDecimal qtyGood) {
        BigDecimal std = jdbc.queryForList("SELECT total FROM fin.std_cost WHERE plant_id = ? AND item_id = ? AND current", BigDecimal.class,
                w.getPlantId(), w.getProductItemId()).stream().findFirst().orElse(BigDecimal.ZERO);
        if (std.signum() > 0 || qtyGood.signum() == 0) {
            return std;
        }
        BigDecimal material = jdbc.queryForObject("""
                SELECT COALESCE((SELECT SUM(m.qty * m.unit_cost) FROM scm.stock_move m JOIN pre.material_request r
                                 ON m.ref_doc_type = 'MR' AND m.ref_doc_id = r.id WHERE r.wo_id = ?), 0)
                     - COALESCE((SELECT SUM(m.qty * m.unit_cost) FROM scm.stock_move m JOIN pre.material_return r
                                 ON m.ref_doc_type = 'MRT' AND m.ref_doc_id = r.id WHERE r.wo_id = ?), 0)""", BigDecimal.class, w.getId(), w.getId());
        return material.divide(qtyGood, 6, RoundingMode.HALF_UP);
    }

    /** Posting: lot batch Quarantine dibuat & stok masuk karantina bernilai; WIP → barang jadi; WO selesai. Rilis hanya oleh QA (QMS-09). */
    @Override
    public void onPost(ProductionOutput d) {
        validateSubmit(d);
        WorkOrder w = wo(d);
        Lot lot = inventory.createLot(w.getProductItemId(), w.getBatchNo(), null, w.getMfgDate(), w.getExpDate(), d.getDocNo());
        BigDecimal unit = unitValue(w, d.getQtyGood());
        d.setUnitCost(unit);
        d.setTotalCost(d.getQtyGood().multiply(unit).setScale(2, RoundingMode.HALF_UP));
        if (d.getQtyGood().signum() > 0) {
            inventory.move(new InventoryService.MoveCommand(InventoryService.MoveType.PRODUCE, w.getProductItemId(), lot.getId(),
                    null, d.getToLocationId(), d.getQtyGood(), unit, d.getDocDate(), "HP", d.getId(), d.getDocNo(),
                    "Hasil produksi " + w.getDocNo()));
        }
        String type = inventory.requireItem(w.getProductItemId()).type();
        if (d.getTotalCost().signum() > 0 && !"WIP".equals(type)) {
            String desc = "Hasil produksi " + w.getBatchNo() + " · " + d.getDocNo();
            journals.postIds(d.getPlantId(), d.getDocDate(), List.of(
                    new JournalPostingService.IdLine(fin.inventoryAccount(type), null, desc, d.getTotalCost(), null),
                    new JournalPostingService.IdLine(fin.inventoryAccount("WIP"), wip.lineCostCenter(w.getId()), desc, null, d.getTotalCost())),
                    "HP", d.getId(), d.getDocNo(), desc);
        }
        d.setLotId(lot.getId());
        w.setQtyGood(d.getQtyGood());
        w.setQtyReject(d.getQtyReject());
        w.setYieldPct(d.getYieldPct());
        w.setFinishedAt(time.now());
        workOrders.save(w);
        workflow.markDone("WO", w.getId(), "Selesai: hasil produksi " + d.getDocNo() + " diposting, batch " + w.getBatchNo() + " ke karantina");
    }
}

@RestController
@RequestMapping("/api/pre/outputs")
class ProductionOutputController extends DocumentApi<ProductionOutput> {

    private final ProductionOutputHandler out;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;
    private final TimeService time;

    ProductionOutputController(ProductionOutputHandler handler, ProductionOutputRepository repo, Support support, JdbcTemplate jdbc,
                               PermissionService perm, TimeService time) {
        super(handler, repo, support, ProductionOutput.class);
        this.out = handler;
        this.jdbc = jdbc;
        this.perm = perm;
        this.time = time;
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("yieldPct", "lotId", "unitCost", "totalCost", "receivedQty", "receivedBy", "receivedAt", "receiveNote");
    }

    @Override
    protected void beforeSave(ProductionOutput d, boolean isNew) {
        require(d.getWoId() != null, "HP_WO", "Pilih work order");
        require(d.getQtyGood() != null && d.getQtyGood().signum() >= 0, "HP_QTY", "Jumlah baik wajib diisi");
        if (d.getQtyReject() == null) {
            d.setQtyReject(BigDecimal.ZERO);
        }
        WorkOrder w = out.wo(d);
        d.setYieldPct(d.getQtyGood().multiply(BigDecimal.valueOf(100)).divide(w.getQtyPlan(), 2, RoundingMode.HALF_UP));
        if (d.getToLocationId() == null) {
            d.setToLocationId(jdbc.queryForList("""
                    SELECT l.id FROM sys.location l JOIN sys.warehouse w ON w.id = l.warehouse_id
                    WHERE w.type = 'FG' AND w.plant_id = ? AND l.is_quarantine AND l.active ORDER BY l.id LIMIT 1""",
                    Long.class, w.getPlantId()).stream().findFirst()
                    .orElseThrow(() -> new BusinessException("HP_LOC", "Belum ada lokasi karantina di gudang barang jadi (SYS-09)")));
        }
    }

    @Override
    protected void enrich(Map<String, Object> body, ProductionOutput d) {
        jdbc.query("""
                SELECT w.doc_no, w.batch_no, w.qty_plan, i.name, pp.min_yield_pct FROM pre.work_order w JOIN sys.item i ON i.id = w.product_item_id
                LEFT JOIN pre.product_param pp ON pp.item_id = i.id WHERE w.id = ?""", rs -> {
            body.put("woDocNo", rs.getString(1));
            body.put("batchNo", rs.getString(2));
            body.put("qtyPlan", rs.getBigDecimal(3));
            body.put("productName", rs.getString(4));
            body.put("minYieldPct", rs.getBigDecimal(5));
        }, d.getWoId());
        if (d.getLotId() != null) {
            body.put("lotStatus", jdbc.queryForObject("SELECT qc_status FROM scm.lot WHERE id = ?", String.class, d.getLotId()));
        }
    }

    record ReceiveRequest(BigDecimal qty, String note) {
    }

    /** SCM-24: gudang barang jadi mengonfirmasi serah terima (qty fisik); selisih wajib dijelaskan. */
    @PostMapping("/{id}/receive")
    @Transactional
    public Envelope receive(@PathVariable Long id, @RequestBody ReceiveRequest req) {
        perm.require("SCM-24", Action.CREATE);
        ProductionOutput d = out.load(id);
        require(d.getStatus() == DocStatus.POSTED, "HP_STATE", "Hasil produksi belum diposting");
        require(d.getReceivedAt() == null, "HP_RECEIVED", "Serah terima sudah dikonfirmasi");
        require(req.qty() != null && req.qty().signum() >= 0, "HP_QTY", "Isi qty fisik yang diterima");
        require(req.qty().compareTo(d.getQtyGood()) == 0 || (req.note() != null && !req.note().isBlank()), "HP_DIFF",
                "Qty fisik berbeda dengan laporan produksi: jelaskan selisihnya");
        d.setReceivedQty(req.qty());
        d.setReceiveNote(req.note());
        d.setReceivedBy(UserContext.userId());
        d.setReceivedAt(time.now());
        out.save(d);
        return envelope(d);
    }
}
