package id.herbatech.erp.scm;

import id.herbatech.erp.fin.FinApi;
import id.herbatech.erp.fin.JournalPostingService;
import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.domain.DocStatus;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.web.MasterController;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static id.herbatech.erp.scm.InventoryService.MoveType;
import static id.herbatech.erp.scm.ScmSupport.money;

interface StockCountRepository extends DocumentRepository<StockCount> {
}

interface StockAdjustmentRepository extends DocumentRepository<StockAdjustment> {
}

interface ScrapRepository extends DocumentRepository<Scrap> {
}

/** Penyesuaian stok + jurnal selisih persediaan; dipakai penyesuaian (SCM-42) dan hasil opname (SCM-41). */
@Component
class StockAdjuster {

    record Adj(Long itemId, Long lotId, Long locationId, BigDecimal qtyDelta, BigDecimal unitCost) {
    }

    private final InventoryService inventory;
    private final ScmSupport scm;
    private final FinApi fin;

    StockAdjuster(InventoryService inventory, ScmSupport scm, FinApi fin) {
        this.inventory = inventory;
        this.scm = scm;
        this.fin = fin;
    }

    /** Biaya satuan: stok yang berkurang memakai biaya lot-lokasi; tambahan memakai biaya isian atau rata-rata item. */
    BigDecimal costOf(Long itemId, Long lotId, Long locationId, BigDecimal qtyDelta, BigDecimal entered) {
        if (qtyDelta.signum() < 0) {
            return inventory.unitCost(itemId, lotId, locationId);
        }
        if (entered != null && entered.signum() > 0) {
            return entered;
        }
        BigDecimal here = inventory.unitCost(itemId, lotId, locationId);
        if (here.signum() > 0) {
            return here;
        }
        BigDecimal avg = inventory.averageCost(itemId, lotId);
        return avg.signum() > 0 ? avg : inventory.averageCost(itemId, null);
    }

    /** @return nilai bersih penyesuaian (positif = selisih lebih). */
    BigDecimal post(Long plantId, LocalDate date, String docType, Long docId, String docNo, String reason, List<Adj> adjs) {
        Map<String, BigDecimal> gain = new LinkedHashMap<>();
        Map<String, BigDecimal> loss = new LinkedHashMap<>();
        BigDecimal net = BigDecimal.ZERO;
        for (Adj a : adjs) {
            if (a.qtyDelta().signum() == 0) {
                continue;
            }
            InventoryService.ItemInfo i = inventory.requireItem(a.itemId());
            BigDecimal qty = a.qtyDelta().abs();
            boolean plus = a.qtyDelta().signum() > 0;
            inventory.move(new InventoryService.MoveCommand(MoveType.ADJUST, a.itemId(), a.lotId(), plus ? null : a.locationId(),
                    plus ? a.locationId() : null, qty, a.unitCost(), date, docType, docId, docNo, reason));
            BigDecimal value = money(qty.multiply(a.unitCost()));
            (plus ? gain : loss).merge(i.type(), value, BigDecimal::add);
            net = plus ? net.add(value) : net.subtract(value);
        }
        Long contra = fin.accountId("6402");
        scm.postInventory(plantId, date, gain, true, contra, null, docType, docId, docNo, "Selisih lebih persediaan " + docNo);
        scm.postInventory(plantId, date, loss, false, contra, null, docType, docId, docNo, "Selisih kurang persediaan " + docNo);
        return net;
    }
}

// =====================================================================================================================
// SCM-42 Penyesuaian stok
// =====================================================================================================================

@Component
class StockAdjustmentHandler implements DocumentHandler<StockAdjustment> {

    private final StockAdjustmentRepository repo;
    private final StockAdjuster adjuster;
    private final InventoryService inventory;
    private final JournalPostingService journals;

    StockAdjustmentHandler(StockAdjustmentRepository repo, StockAdjuster adjuster, InventoryService inventory, JournalPostingService journals) {
        this.repo = repo;
        this.adjuster = adjuster;
        this.inventory = inventory;
        this.journals = journals;
    }

    @Override public String docType() { return "ADJ"; }
    @Override public String periodModule() { return "SCM"; }
    @Override public StockAdjustment load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Penyesuaian", id)); }
    @Override public StockAdjustment save(StockAdjustment doc) { return repo.save(doc); }
    @Override public boolean autoPost(StockAdjustment doc) { return true; }

    /** Nilai mutlak untuk matriks approval (selisih lebih maupun kurang sama-sama butuh persetujuan). */
    @Override
    public BigDecimal amount(StockAdjustment d) {
        return d.getLines().stream().map(l -> l.getAmount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public String summary(StockAdjustment d) {
        return "%d baris · %s".formatted(d.getLines().size(), d.getReason() == null ? "" : d.getReason());
    }

    @Override
    public void validateSubmit(StockAdjustment d) {
        if (d.getReason() == null || d.getReason().trim().length() < 5) {
            throw new BusinessException("ADJ_REASON", "Alasan penyesuaian wajib dijelaskan");
        }
        if (d.getLines().isEmpty()) {
            throw new BusinessException("ADJ_LINES", "Isi minimal satu baris");
        }
        for (StockAdjustment.Line l : d.getLines()) {
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            if (l.getLocationId() == null || l.getQtyDelta() == null || l.getQtyDelta().signum() == 0) {
                throw new BusinessException("ADJ_LINE", "Baris " + l.getLineNo() + ": lokasi & selisih qty (±) wajib diisi");
            }
            if (i.lotTracked() && l.getLotId() == null) {
                throw new BusinessException("ADJ_LOT", "Baris " + l.getLineNo() + " (" + i.code() + "): pilih lot");
            }
            if (l.getQtyDelta().signum() < 0) {
                BigDecimal have = inventory.onHand(l.getItemId(), l.getLotId(), l.getLocationId());
                if (have.compareTo(l.getQtyDelta().negate()) < 0) {
                    throw BusinessException.of("STOCK_NEGATIVE", "Baris %d: stok hanya %s; tidak boleh negatif", l.getLineNo(),
                            have.stripTrailingZeros().toPlainString());
                }
            }
        }
    }

    @Override
    public void onPost(StockAdjustment d) {
        validateSubmit(d);
        d.setTotalValue(adjuster.post(d.getPlantId(), d.getDocDate(), "ADJ", d.getId(), d.getDocNo(), d.getReason(),
                d.getLines().stream().map(l -> new StockAdjuster.Adj(l.getItemId(), l.getLotId(), l.getLocationId(), l.getQtyDelta(),
                        l.getUnitCost())).toList()));
    }

    @Override
    public StockAdjustment reverse(StockAdjustment d, LocalDate date, String reason) {
        for (StockAdjustment.Line l : d.getLines()) {
            boolean wasPlus = l.getQtyDelta().signum() > 0;
            inventory.move(new InventoryService.MoveCommand(MoveType.ADJUST, l.getItemId(), l.getLotId(), wasPlus ? l.getLocationId() : null,
                    wasPlus ? null : l.getLocationId(), l.getQtyDelta().abs(), l.getUnitCost(), date, "ADJ", d.getId(), d.getDocNo(),
                    "Reversal: " + reason));
        }
        journals.reverseFor("ADJ", d.getId(), date, reason);
        return d;
    }
}

@RestController
@RequestMapping("/api/scm/adjustments")
class StockAdjustmentController extends DocumentApi<StockAdjustment> {

    private final StockAdjuster adjuster;
    private final ScmSupport scm;

    StockAdjustmentController(StockAdjustmentHandler handler, StockAdjustmentRepository repo, Support support, StockAdjuster adjuster,
                              ScmSupport scm) {
        super(handler, repo, support, StockAdjustment.class);
        this.adjuster = adjuster;
        this.scm = scm;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "reason");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("totalValue", "countId");
    }

    @Override
    protected void apply(StockAdjustment target, StockAdjustment in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (StockAdjustment.Line l : in.getLines()) {
            l.setId(null);
            l.setAdjustment(target);
            l.setLineNo(no++);
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(StockAdjustment d, boolean isNew) {
        BigDecimal net = BigDecimal.ZERO;
        for (StockAdjustment.Line l : d.getLines()) {
            require(l.getItemId() != null && l.getLocationId() != null && l.getQtyDelta() != null, "ADJ_LINE",
                    "Baris " + l.getLineNo() + ": item, lokasi, dan selisih qty wajib diisi");
            l.setUnitCost(adjuster.costOf(l.getItemId(), l.getLotId(), l.getLocationId(), l.getQtyDelta(), l.getUnitCost()));
            l.setAmount(money(l.getQtyDelta().multiply(l.getUnitCost())));
            net = net.add(l.getAmount());
        }
        d.setTotalValue(net);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, StockAdjustment d) {
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                m.put("lotNo", m.get("lotId") == null ? null : scm.lotNo(((Number) m.get("lotId")).longValue()));
            }
        }
    }
}

// =====================================================================================================================
// SCM-41 Stock opname & cycle count
// =====================================================================================================================

@Component
class StockCountHandler implements DocumentHandler<StockCount> {

    private final StockCountRepository repo;
    private final StockAdjuster adjuster;
    private final JdbcTemplate jdbc;

    StockCountHandler(StockCountRepository repo, StockAdjuster adjuster, JdbcTemplate jdbc) {
        this.repo = repo;
        this.adjuster = adjuster;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "CNT"; }
    @Override public String periodModule() { return "SCM"; }
    @Override public StockCount load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Opname", id)); }
    @Override public StockCount save(StockCount doc) { return repo.save(doc); }
    @Override public boolean autoPost(StockCount doc) { return true; }
    @Override public BigDecimal amount(StockCount d) { return d.getDiffValue().abs(); }

    @Override
    public boolean allows(String action, StockCount doc) {
        return !"REVERSE".equals(action);
    }

    @Override
    public String summary(StockCount d) {
        String wh = jdbc.queryForList("SELECT code || ' ' || name FROM sys.warehouse WHERE id = ?", String.class, d.getWarehouseId())
                .stream().findFirst().orElse("?");
        return "%s · %s · %d baris".formatted(wh, "FULL".equals(d.getKind()) ? "opname penuh" : "cycle count", d.getLines().size());
    }

    void recompute(StockCount d) {
        int accurate = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (StockCount.Line l : d.getLines()) {
            if (l.getCountedQty() == null) {
                l.setDiffQty(BigDecimal.ZERO);
                l.setDiffValue(BigDecimal.ZERO);
                continue;
            }
            l.setDiffQty(l.getCountedQty().subtract(l.getSystemQty()));
            if (l.getUnitCost() == null || l.getUnitCost().signum() == 0) {
                l.setUnitCost(adjuster.costOf(l.getItemId(), l.getLotId(), l.getLocationId(), BigDecimal.ONE, null));
            }
            l.setDiffValue(money(l.getDiffQty().multiply(l.getUnitCost())));
            if (l.getDiffQty().signum() == 0) {
                accurate++;
            }
            value = value.add(l.getDiffValue());
        }
        d.setLinesTotal(d.getLines().size());
        d.setLinesAccurate(accurate);
        d.setDiffValue(value);
    }

    @Override
    public void validateSubmit(StockCount d) {
        if (d.getLines().isEmpty()) {
            throw new BusinessException("CNT_LINES", "Ambil saldo sistem dulu (tombol \"Ambil saldo\")");
        }
        for (StockCount.Line l : d.getLines()) {
            if (l.getCountedQty() == null || l.getCountedQty().signum() < 0) {
                throw new BusinessException("CNT_QTY", "Baris " + l.getLineNo() + ": hasil hitung belum diisi");
            }
            if (l.getDiffQty().signum() != 0 && (l.getNote() == null || l.getNote().isBlank())) {
                throw new BusinessException("CNT_NOTE", "Baris " + l.getLineNo() + ": jelaskan penyebab selisih");
            }
        }
        recompute(d);
    }

    /** Selisih menjadi penyesuaian stok (gerak ADJUST) dan jurnal selisih persediaan. */
    @Override
    public void onPost(StockCount d) {
        recompute(d);
        adjuster.post(d.getPlantId(), d.getDocDate(), "CNT", d.getId(), d.getDocNo(), "Hasil opname " + d.getDocNo(),
                d.getLines().stream().filter(l -> l.getDiffQty().signum() != 0)
                        .map(l -> new StockAdjuster.Adj(l.getItemId(), l.getLotId(), l.getLocationId(), l.getDiffQty(), l.getUnitCost())).toList());
    }
}

@RestController
@RequestMapping("/api/scm/counts")
class StockCountController extends DocumentApi<StockCount> {

    private final StockCountHandler handler;
    private final ScmSupport scm;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;

    StockCountController(StockCountHandler handler, StockCountRepository repo, Support support, ScmSupport scm, JdbcTemplate jdbc,
                         PermissionService perm) {
        super(handler, repo, support, StockCount.class);
        this.handler = handler;
        this.scm = scm;
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("linesTotal", "linesAccurate", "diffValue");
    }

    /** Hanya qty hitung & catatan yang bisa diubah; saldo sistem dibekukan saat lembar hitung dibuat. */
    @Override
    protected void apply(StockCount target, StockCount in, boolean isNew) {
        super.apply(target, in, isNew);
        if (isNew) {
            return;
        }
        Map<Long, StockCount.Line> incoming = new LinkedHashMap<>();
        List<StockCount.Line> added = new java.util.ArrayList<>();
        for (StockCount.Line l : in.getLines()) {
            if (l.getId() != null) {
                incoming.put(l.getId(), l);
            } else {
                added.add(l);
            }
        }
        target.getLines().removeIf(l -> !incoming.containsKey(l.getId()) && l.getSystemQty().signum() == 0);
        for (StockCount.Line l : target.getLines()) {
            StockCount.Line in1 = incoming.get(l.getId());
            if (in1 != null) {
                l.setCountedQty(in1.getCountedQty());
                l.setNote(in1.getNote());
            }
        }
        short no = (short) (target.getLines().stream().mapToInt(StockCount.Line::getLineNo).max().orElse(0) + 1);
        for (StockCount.Line l : added) {
            require(l.getItemId() != null && l.getLocationId() != null, "CNT_LINE", "Baris temuan wajib berisi item & lokasi");
            l.setId(null);
            l.setCount(target);
            l.setLineNo(no++);
            l.setSystemQty(BigDecimal.ZERO);
            l.setUnitCost(BigDecimal.ZERO);
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(StockCount d, boolean isNew) {
        require(d.getWarehouseId() != null, "CNT_WH", "Pilih gudang");
        handler.recompute(d);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, StockCount d) {
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                m.put("itemLabel", scm.itemLabel(((Number) m.get("itemId")).longValue()));
                m.put("lotNo", m.get("lotId") == null ? null : scm.lotNo(((Number) m.get("lotId")).longValue()));
                m.put("locationLabel", scm.locationLabel(((Number) m.get("locationId")).longValue()));
            }
        }
    }

    /** Bekukan saldo sistem gudang ke lembar hitung (draft). Cycle count: hanya lokasi yang dipilih di catatan tidak dibedakan. */
    @PostMapping("/{id}/snapshot")
    @Transactional
    public Envelope snapshot(@PathVariable Long id) {
        perm.require("SCM-41", Action.CREATE);
        StockCount d = handler.load(id);
        require(d.getStatus() == DocStatus.DRAFT, "CNT_STATE", "Saldo hanya bisa diambil saat draft");
        d.getLines().clear();
        short[] no = {1};
        jdbc.query("""
                SELECT q.item_id, q.lot_id, q.location_id, q.qty, q.unit_cost FROM scm.stock_quant q
                JOIN sys.location l ON l.id = q.location_id JOIN sys.item i ON i.id = q.item_id
                WHERE l.warehouse_id = ? AND q.qty > 0 ORDER BY l.bin_code, i.code, q.lot_id""", rs -> {
            StockCount.Line l = new StockCount.Line();
            l.setCount(d);
            l.setLineNo(no[0]++);
            l.setItemId(rs.getLong(1));
            l.setLotId((Long) rs.getObject(2));
            l.setLocationId(rs.getLong(3));
            l.setSystemQty(rs.getBigDecimal(4));
            l.setUnitCost(rs.getBigDecimal(5));
            d.getLines().add(l);
        }, d.getWarehouseId());
        handler.recompute(d);
        handler.save(d);
        workflow().touched(handler, d);
        return envelope(handler.load(id));
    }
}

// =====================================================================================================================
// SCM-46 Pemusnahan barang
// =====================================================================================================================

@Component
class ScrapHandler implements DocumentHandler<Scrap> {

    private final ScrapRepository repo;
    private final InventoryService inventory;
    private final ScmSupport scm;
    private final FinApi fin;

    ScrapHandler(ScrapRepository repo, InventoryService inventory, ScmSupport scm, FinApi fin) {
        this.repo = repo;
        this.inventory = inventory;
        this.scm = scm;
        this.fin = fin;
    }

    @Override public String docType() { return "SCR"; }
    @Override public String periodModule() { return "SCM"; }
    @Override public Scrap load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Pemusnahan", id)); }
    @Override public Scrap save(Scrap doc) { return repo.save(doc); }
    @Override public BigDecimal amount(Scrap d) { return d.getTotalValue(); }

    @Override
    public boolean allows(String action, Scrap doc) {
        return !"REVERSE".equals(action);
    }

    @Override
    public String summary(Scrap d) {
        return "%d baris · %s".formatted(d.getLines().size(), d.getReason() == null ? "" : d.getReason());
    }

    @Override
    public void validateSubmit(Scrap d) {
        if (d.getReason() == null || d.getReason().isBlank()) {
            throw new BusinessException("SCR_REASON", "Alasan pemusnahan wajib diisi");
        }
        if (d.getLines().isEmpty()) {
            throw new BusinessException("SCR_LINES", "Isi minimal satu baris");
        }
        for (Scrap.Line l : d.getLines()) {
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            if (i.lotTracked() && l.getLotId() == null) {
                throw new BusinessException("SCR_LOT", "Baris " + l.getLineNo() + " (" + i.code() + "): pilih lot");
            }
            BigDecimal have = inventory.onHand(l.getItemId(), l.getLotId(), l.getLocationId());
            if (l.getQty() == null || l.getQty().signum() <= 0 || have.compareTo(l.getQty()) < 0) {
                throw BusinessException.of("SCR_QTY", "Baris %d: qty harus > 0 dan tidak melebihi stok (%s)", l.getLineNo(),
                        have.stripTrailingZeros().toPlainString());
            }
        }
    }

    /** Barang keluar (gerak SCRAP, status lot apa pun), beban pemusnahan; berita acara ditandatangani elektronik saat posting. */
    @Override
    public void onPost(Scrap d) {
        validateSubmit(d);
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Scrap.Line l : d.getLines()) {
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            BigDecimal cost = inventory.unitCost(l.getItemId(), l.getLotId(), l.getLocationId());
            inventory.move(new InventoryService.MoveCommand(MoveType.SCRAP, l.getItemId(), l.getLotId(), l.getLocationId(), null, l.getQty(),
                    cost, d.getDocDate(), "SCR", d.getId(), d.getDocNo(), d.getReason()));
            l.setUnitCost(cost);
            l.setAmount(money(l.getQty().multiply(cost)));
            total = total.add(l.getAmount());
            byType.merge(i.type(), l.getAmount(), BigDecimal::add);
        }
        d.setTotalValue(total);
        scm.postInventory(d.getPlantId(), d.getDocDate(), byType, false, fin.accountId("6401"), null, "SCR", d.getId(), d.getDocNo(),
                "Pemusnahan " + d.getDocNo());
    }
}

@RestController
@RequestMapping("/api/scm/scraps")
class ScrapController extends DocumentApi<Scrap> {

    private final InventoryService inventory;
    private final ScmSupport scm;

    ScrapController(ScrapHandler handler, ScrapRepository repo, Support support, InventoryService inventory, ScmSupport scm) {
        super(handler, repo, support, Scrap.class);
        this.inventory = inventory;
        this.scm = scm;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "reason");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("totalValue");
    }

    @Override
    protected void apply(Scrap target, Scrap in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (Scrap.Line l : in.getLines()) {
            l.setId(null);
            l.setScrap(target);
            l.setLineNo(no++);
            target.getLines().add(l);
        }
    }

    /** Nilai perkiraan untuk matriks approval (biaya lot-lokasi saat ini). */
    @Override
    protected void beforeSave(Scrap d, boolean isNew) {
        BigDecimal total = BigDecimal.ZERO;
        for (Scrap.Line l : d.getLines()) {
            require(l.getItemId() != null && l.getLocationId() != null && l.getQty() != null, "SCR_LINE",
                    "Baris " + l.getLineNo() + ": item, lokasi, qty wajib diisi");
            l.setUnitCost(inventory.unitCost(l.getItemId(), l.getLotId(), l.getLocationId()));
            l.setAmount(money(l.getQty().multiply(l.getUnitCost())));
            total = total.add(l.getAmount());
        }
        d.setTotalValue(total);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, Scrap d) {
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                m.put("lotNo", m.get("lotId") == null ? null : scm.lotNo(((Number) m.get("lotId")).longValue()));
            }
        }
    }
}

// =====================================================================================================================
// SCM-43 Parameter stok
// =====================================================================================================================

@RestController
@RequestMapping("/api/scm/stock-params")
class StockParamController extends MasterController<StockParam> {

    StockParamController(StockParamRepository r, PermissionService p) {
        super(r, p, StockParam.class);
    }

    @Override protected String menuCode() { return "SCM-43"; }
    @Override protected List<String> searchFields() { return List.of(); }
    @Override protected Sort defaultSort() { return Sort.by("itemId"); }
    @Override protected String[] immutableFields() { return new String[]{"itemId", "plantId"}; }
}
