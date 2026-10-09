package id.herbatech.erp.scm;

import id.herbatech.erp.fin.FinApi;
import id.herbatech.erp.fin.JournalPostingService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static id.herbatech.erp.scm.InventoryService.MoveType;
import static id.herbatech.erp.scm.ScmSupport.money;
import static id.herbatech.erp.scm.ScmSupport.nz;

interface GoodsReceiptRepository extends DocumentRepository<GoodsReceipt> {
}

interface StockTransferRepository extends DocumentRepository<StockTransfer> {
}

interface GoodsIssueRepository extends DocumentRepository<GoodsIssue> {
}

interface DeliveryRepository extends DocumentRepository<Delivery> {
}

interface CustomerReturnRepository extends DocumentRepository<CustomerReturn> {
}

// =====================================================================================================================
// SCM-20 Penerimaan barang (GR)
// =====================================================================================================================

@Component
class GoodsReceiptHandler implements DocumentHandler<GoodsReceipt> {

    private final GoodsReceiptRepository repo;
    private final InventoryService inventory;
    private final NumberingService numbering;
    private final ScmSupport scm;
    private final FinApi fin;
    private final JournalPostingService journals;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;

    GoodsReceiptHandler(GoodsReceiptRepository repo, InventoryService inventory, NumberingService numbering, ScmSupport scm, FinApi fin,
                        JournalPostingService journals, ApplicationEventPublisher events, JdbcTemplate jdbc) {
        this.repo = repo;
        this.inventory = inventory;
        this.numbering = numbering;
        this.scm = scm;
        this.fin = fin;
        this.journals = journals;
        this.events = events;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "GR"; }
    @Override public String periodModule() { return "SCM"; }
    @Override public GoodsReceipt load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Penerimaan barang", id)); }
    @Override public GoodsReceipt save(GoodsReceipt doc) { return repo.save(doc); }
    @Override public boolean autoPost(GoodsReceipt doc) { return true; }
    @Override public BigDecimal amount(GoodsReceipt d) { return d.getTotal(); }

    @Override
    public String summary(GoodsReceipt d) {
        String po = jdbc.queryForList("SELECT doc_no FROM prc.po WHERE id = ?", String.class, d.getPoId()).stream().findFirst().orElse("?");
        return "%s · %s%s".formatted(scm.partnerName(d.getPartnerId()), po, d.getDeliveryNoteNo() == null ? "" : " · SJ " + d.getDeliveryNoteNo());
    }

    Map<String, Object> po(Long poId) {
        return jdbc.queryForList("SELECT status, partner_id, doc_no, exchange_rate FROM prc.po WHERE id = ?", poId).stream().findFirst()
                .orElseThrow(() -> new BusinessException("GR_PO", "Purchase order tidak ditemukan"));
    }

    @Override
    public void validateSubmit(GoodsReceipt d) {
        Map<String, Object> po = po(d.getPoId());
        if (!"APPROVED".equals(po.get("status"))) {
            throw new BusinessException("GR_PO", "PO " + po.get("doc_no") + " belum disetujui atau sudah selesai/ditutup");
        }
        if (d.getLines().isEmpty()) {
            throw new BusinessException("GR_LINES", "Isi minimal satu baris penerimaan");
        }
        BigDecimal tol = fin.paramNum("GR_OVER_PCT", "2");
        Map<Long, BigDecimal> perLine = new LinkedHashMap<>();
        for (GoodsReceipt.Line l : d.getLines()) {
            if (l.getQty() == null || l.getQty().signum() <= 0) {
                throw new BusinessException("GR_QTY", "Baris " + l.getLineNo() + ": qty diterima harus lebih dari nol");
            }
            perLine.merge(l.getPoLineId(), l.getQty(), BigDecimal::add);
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            if (i.lotTracked()) {
                if (l.getSupplierLot() == null || l.getSupplierLot().isBlank()) {
                    throw new BusinessException("GR_LOT", "Baris " + l.getLineNo() + " (" + i.code() + "): nomor lot supplier wajib diisi");
                }
                if (i.shelfLifeDays() != null && l.getExpDate() == null) {
                    throw new BusinessException("GR_ED", "Baris " + l.getLineNo() + " (" + i.code() + "): tanggal kedaluwarsa wajib diisi");
                }
                if (l.getExpDate() != null && !l.getExpDate().isAfter(d.getDocDate())) {
                    throw new BusinessException("GR_ED", "Baris " + l.getLineNo() + " (" + i.code() + "): barang sudah kedaluwarsa, tolak penerimaan");
                }
                if (!scm.isQuarantine(l.getLocationId())) {
                    throw new BusinessException("GR_QRN", "Baris " + l.getLineNo() + ": bahan dilacak per lot wajib masuk lokasi karantina");
                }
            }
        }
        for (Map.Entry<Long, BigDecimal> e : perLine.entrySet()) {
            Map<String, Object> pl = jdbc.queryForList("SELECT po_id, qty, received_qty, closed FROM prc.po_line WHERE id = ?", e.getKey())
                    .stream().findFirst().orElseThrow(() -> new BusinessException("GR_LINE", "Baris PO tidak ditemukan"));
            if (!d.getPoId().equals(((Number) pl.get("po_id")).longValue()) || (Boolean) pl.get("closed")) {
                throw new BusinessException("GR_LINE", "Baris PO bukan milik PO ini atau sudah ditutup");
            }
            BigDecimal qty = (BigDecimal) pl.get("qty");
            BigDecimal max = qty.subtract((BigDecimal) pl.get("received_qty"))
                    .add(qty.multiply(tol).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            if (e.getValue().compareTo(max) > 0) {
                throw BusinessException.of("GR_OVER", "Qty diterima %s melebihi sisa PO + toleransi %s%% (maks %s)",
                        e.getValue().stripTrailingZeros().toPlainString(), tol.stripTrailingZeros().toPlainString(),
                        max.stripTrailingZeros().toPlainString());
            }
        }
    }

    static String lotPrefix(String itemType) {
        return switch (itemType) {
            case "RM" -> "R";
            case "PM" -> "K";
            case "SP" -> "S";
            default -> "L";
        };
    }

    /** Lot internal Quarantine per baris, stok masuk, jurnal persediaan vs GRNI, qty diterima PO diperbarui. */
    @Override
    public void onPost(GoodsReceipt d) {
        validateSubmit(d);
        Map<String, Object> po = po(d.getPoId());
        BigDecimal rate = (BigDecimal) po.get("exchange_rate");
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        List<GoodsReceived.Line> ev = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (GoodsReceipt.Line l : d.getLines()) {
            Map<String, Object> pl = jdbc.queryForMap("SELECT unit_price, discount_pct FROM prc.po_line WHERE id = ?", l.getPoLineId());
            BigDecimal unit = ((BigDecimal) pl.get("unit_price"))
                    .multiply(BigDecimal.ONE.subtract(((BigDecimal) pl.get("discount_pct")).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)))
                    .multiply(rate).setScale(6, RoundingMode.HALF_UP);
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            Long lotId = null;
            if (i.lotTracked()) {
                lotId = inventory.createLot(l.getItemId(), numbering.nextBatchNo(lotPrefix(i.type()), d.getDocDate()), l.getSupplierLot(),
                        l.getMfgDate(), l.getExpDate(), d.getDocNo()).getId();
            }
            inventory.move(new InventoryService.MoveCommand(MoveType.RECEIPT, l.getItemId(), lotId, null, l.getLocationId(), l.getQty(),
                    unit, d.getDocDate(), "GR", d.getId(), d.getDocNo(), "Penerimaan " + po.get("doc_no")));
            l.setLotId(lotId);
            l.setUnitCost(unit);
            l.setAmount(money(l.getQty().multiply(unit)));
            total = total.add(l.getAmount());
            byType.merge(i.type(), l.getAmount(), BigDecimal::add);
            ev.add(new GoodsReceived.Line(l.getPoLineId(), l.getQty(), l.getAmount()));
        }
        d.setTotal(total);
        scm.postInventory(d.getPlantId(), d.getDocDate(), byType, true, fin.accountId("2102"), null, "GR", d.getId(), d.getDocNo(),
                "Penerimaan " + d.getDocNo() + " · " + scm.partnerName(d.getPartnerId()));
        events.publishEvent(new GoodsReceived(d.getId(), d.getDocNo(), d.getPoId(), d.getDocDate(), ev));
    }

    /** Reversal hanya bila stok lot masih utuh di lokasi penerimaan dan belum difakturkan melebihi sisa terima. */
    @Override
    public GoodsReceipt reverse(GoodsReceipt d, LocalDate date, String reason) {
        for (GoodsReceipt.Line l : d.getLines()) {
            BigDecimal invoiced = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(il.qty), 0) FROM fin.ap_invoice_line il JOIN fin.ap_invoice i ON i.id = il.invoice_id
                    WHERE il.po_line_id = ? AND i.status IN ('SUBMITTED','APPROVED','POSTED')""", BigDecimal.class, l.getPoLineId());
            BigDecimal received = jdbc.queryForObject("SELECT received_qty FROM prc.po_line WHERE id = ?", BigDecimal.class, l.getPoLineId());
            if (invoiced.compareTo(received.subtract(l.getQty())) > 0) {
                throw new BusinessException("GR_INVOICED", "Penerimaan sudah difakturkan supplier (FIN-10); batalkan fakturnya dulu");
            }
        }
        List<GoodsReceived.Line> ev = new ArrayList<>();
        for (GoodsReceipt.Line l : d.getLines()) {
            inventory.move(new InventoryService.MoveCommand(MoveType.RETURN, l.getItemId(), l.getLotId(), l.getLocationId(), null, l.getQty(),
                    null, date, "GR", d.getId(), d.getDocNo(), "Reversal: " + reason));
            ev.add(new GoodsReceived.Line(l.getPoLineId(), l.getQty().negate(), l.getAmount().negate()));
        }
        journals.reverseFor("GR", d.getId(), date, reason);
        events.publishEvent(new GoodsReceived(d.getId(), d.getDocNo(), d.getPoId(), date, ev));
        return d;
    }
}

@RestController
@RequestMapping("/api/scm/receipts")
class GoodsReceiptController extends DocumentApi<GoodsReceipt> {

    private final GoodsReceiptHandler gr;
    private final InventoryService inventory;
    private final ScmSupport scm;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;

    GoodsReceiptController(GoodsReceiptHandler handler, GoodsReceiptRepository repo, Support support, InventoryService inventory,
                           ScmSupport scm, JdbcTemplate jdbc, PermissionService perm) {
        super(handler, repo, support, GoodsReceipt.class);
        this.gr = handler;
        this.inventory = inventory;
        this.scm = scm;
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "deliveryNoteNo", "notes");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("total", "partnerId");
    }

    @Override
    protected void apply(GoodsReceipt target, GoodsReceipt in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (GoodsReceipt.Line l : in.getLines()) {
            l.setId(null);
            l.setReceipt(target);
            l.setLineNo(no++);
            l.setLotId(null);
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(GoodsReceipt d, boolean isNew) {
        require(d.getPoId() != null, "GR_PO", "Pilih purchase order");
        Map<String, Object> po = gr.po(d.getPoId());
        d.setPartnerId(((Number) po.get("partner_id")).longValue());
        BigDecimal rate = (BigDecimal) po.get("exchange_rate");
        BigDecimal total = BigDecimal.ZERO;
        for (GoodsReceipt.Line l : d.getLines()) {
            require(l.getPoLineId() != null, "GR_LINE", "Setiap baris harus merujuk baris PO");
            Map<String, Object> pl = jdbc.queryForList("SELECT item_id, unit_price, discount_pct FROM prc.po_line WHERE id = ? AND po_id = ?",
                    l.getPoLineId(), d.getPoId()).stream().findFirst()
                    .orElseThrow(() -> new BusinessException("GR_LINE", "Baris PO bukan milik PO ini"));
            l.setItemId(((Number) pl.get("item_id")).longValue());
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            if (l.getLocationId() == null) {
                Long plant = d.getPlantId() == null ? UserContext.plantId() : d.getPlantId();
                l.setLocationId(i.lotTracked() ? inventory.quarantineLocation(plant, i.type()) : inventory.defaultLocation(plant, i.type()));
            }
            if (i.lotTracked() && l.getExpDate() == null && i.shelfLifeDays() != null && l.getMfgDate() != null) {
                l.setExpDate(l.getMfgDate().plusDays(i.shelfLifeDays()));
            }
            BigDecimal unit = ((BigDecimal) pl.get("unit_price")).multiply(rate)
                    .multiply(BigDecimal.ONE.subtract(((BigDecimal) pl.get("discount_pct")).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)));
            l.setUnitCost(unit.setScale(6, RoundingMode.HALF_UP));
            l.setAmount(money(nz(l.getQty()).multiply(unit)));
            total = total.add(l.getAmount());
        }
        d.setTotal(total);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, GoodsReceipt d) {
        body.put("partnerName", scm.partnerName(d.getPartnerId()));
        body.put("poNo", jdbc.queryForList("SELECT doc_no FROM prc.po WHERE id = ?", String.class, d.getPoId()).stream().findFirst().orElse(null));
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                InventoryService.ItemInfo i = inventory.item(((Number) m.get("itemId")).longValue());
                m.put("itemLabel", i.code() + " · " + i.name());
                m.put("uom", i.uom());
                m.put("lotTracked", i.lotTracked());
                m.put("lotNo", m.get("lotId") == null ? null : scm.lotNo(((Number) m.get("lotId")).longValue()));
                m.put("locationLabel", m.get("locationId") == null ? null : scm.locationLabel(((Number) m.get("locationId")).longValue()));
            }
        }
    }

    record OpenPoLine(Long poLineId, Long itemId, String itemLabel, String uom, BigDecimal qty, BigDecimal received, BigDecimal remaining,
                      BigDecimal unitPrice, boolean lotTracked, Integer shelfLifeDays) {
    }

    /** Baris PO yang masih menunggu kedatangan (bahan GR). */
    @GetMapping("/po-lines")
    public List<OpenPoLine> poLines(@RequestParam Long poId) {
        perm.require("SCM-20", Action.VIEW);
        return jdbc.query("""
                SELECT l.id, l.item_id, i.code || ' · ' || i.name, u.code, l.qty, l.received_qty, GREATEST(l.qty - l.received_qty, 0),
                       l.unit_price, i.lot_tracked, i.shelf_life_days
                FROM prc.po_line l JOIN prc.po p ON p.id = l.po_id JOIN sys.item i ON i.id = l.item_id JOIN sys.uom u ON u.id = i.uom_id
                WHERE l.po_id = ? AND p.status = 'APPROVED' AND NOT l.closed AND l.qty > l.received_qty AND i.type <> 'SVC'
                ORDER BY l.line_no""",
                (rs, i) -> new OpenPoLine(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5),
                        rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getBoolean(9), (Integer) rs.getObject(10)), poId);
    }
}

// =====================================================================================================================
// SCM-25 Transfer antar gudang
// =====================================================================================================================

@Component
class StockTransferHandler implements DocumentHandler<StockTransfer> {

    private final StockTransferRepository repo;
    private final InventoryService inventory;
    private final ScmSupport scm;

    StockTransferHandler(StockTransferRepository repo, InventoryService inventory, ScmSupport scm) {
        this.repo = repo;
        this.inventory = inventory;
        this.scm = scm;
    }

    @Override public String docType() { return "TRF"; }
    @Override public String periodModule() { return "SCM"; }
    @Override public StockTransfer load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Transfer", id)); }
    @Override public StockTransfer save(StockTransfer doc) { return repo.save(doc); }
    @Override public boolean autoPost(StockTransfer doc) { return true; }

    @Override
    public String summary(StockTransfer d) {
        if (d.getLines().isEmpty()) {
            return "Transfer stok";
        }
        StockTransfer.Line f = d.getLines().getFirst();
        return "%s → %s · %d baris".formatted(scm.locationLabel(f.getFromLocationId()), scm.locationLabel(f.getToLocationId()), d.getLines().size());
    }

    @Override
    public void validateSubmit(StockTransfer d) {
        if (d.getLines().isEmpty()) {
            throw new BusinessException("TRF_LINES", "Isi minimal satu baris");
        }
        for (StockTransfer.Line l : d.getLines()) {
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            if (l.getFromLocationId() == null || l.getToLocationId() == null || l.getFromLocationId().equals(l.getToLocationId())) {
                throw new BusinessException("TRF_LOC", "Baris " + l.getLineNo() + ": lokasi asal & tujuan wajib diisi dan berbeda");
            }
            if (i.lotTracked() && l.getLotId() == null) {
                throw new BusinessException("TRF_LOT", "Baris " + l.getLineNo() + " (" + i.code() + "): pilih lot");
            }
            if (l.getQty() == null || l.getQty().signum() <= 0) {
                throw new BusinessException("TRF_QTY", "Baris " + l.getLineNo() + ": qty harus lebih dari nol");
            }
            BigDecimal have = inventory.onHand(l.getItemId(), l.getLotId(), l.getFromLocationId());
            if (have.compareTo(l.getQty()) < 0) {
                throw BusinessException.of("STOCK_NEGATIVE", "Baris %d: stok di lokasi asal hanya %s", l.getLineNo(), have.stripTrailingZeros().toPlainString());
            }
        }
    }

    @Override
    public void onPost(StockTransfer d) {
        for (StockTransfer.Line l : d.getLines()) {
            inventory.move(new InventoryService.MoveCommand(MoveType.TRANSFER, l.getItemId(), l.getLotId(), l.getFromLocationId(),
                    l.getToLocationId(), l.getQty(), null, d.getDocDate(), "TRF", d.getId(), d.getDocNo(), d.getNotes()));
        }
    }

    @Override
    public StockTransfer reverse(StockTransfer d, LocalDate date, String reason) {
        for (StockTransfer.Line l : d.getLines()) {
            inventory.move(new InventoryService.MoveCommand(MoveType.TRANSFER, l.getItemId(), l.getLotId(), l.getToLocationId(),
                    l.getFromLocationId(), l.getQty(), null, date, "TRF", d.getId(), d.getDocNo(), "Reversal: " + reason));
        }
        return d;
    }
}

@RestController
@RequestMapping("/api/scm/transfers")
class StockTransferController extends DocumentApi<StockTransfer> {

    private final ScmSupport scm;

    StockTransferController(StockTransferHandler handler, StockTransferRepository repo, Support support, ScmSupport scm) {
        super(handler, repo, support, StockTransfer.class);
        this.scm = scm;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "notes");
    }

    @Override
    protected void apply(StockTransfer target, StockTransfer in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (StockTransfer.Line l : in.getLines()) {
            l.setId(null);
            l.setTransfer(target);
            l.setLineNo(no++);
            target.getLines().add(l);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, StockTransfer d) {
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                m.put("lotNo", m.get("lotId") == null ? null : scm.lotNo(((Number) m.get("lotId")).longValue()));
            }
        }
    }
}

// =====================================================================================================================
// SCM-29 Pengeluaran non-produksi
// =====================================================================================================================

@Component
class GoodsIssueHandler implements DocumentHandler<GoodsIssue> {

    private final GoodsIssueRepository repo;
    private final InventoryService inventory;
    private final FinApi fin;
    private final JournalPostingService journals;
    private final JdbcTemplate jdbc;

    GoodsIssueHandler(GoodsIssueRepository repo, InventoryService inventory, FinApi fin, JournalPostingService journals, JdbcTemplate jdbc) {
        this.repo = repo;
        this.inventory = inventory;
        this.fin = fin;
        this.journals = journals;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "GI"; }
    @Override public String periodModule() { return "SCM"; }
    @Override public GoodsIssue load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Pengeluaran barang", id)); }
    @Override public GoodsIssue save(GoodsIssue doc) { return repo.save(doc); }
    @Override public boolean autoPost(GoodsIssue doc) { return true; }
    @Override public BigDecimal amount(GoodsIssue d) { return d.getTotal(); }

    @Override
    public String summary(GoodsIssue d) {
        String cc = jdbc.queryForList("SELECT code || ' ' || name FROM sys.cost_center WHERE id = ?", String.class, d.getCostCenterId())
                .stream().findFirst().orElse("?");
        return "Ke " + cc + (d.getPurpose() == null ? "" : " · " + d.getPurpose());
    }

    Long accountOf(GoodsIssue.Line l, InventoryService.ItemInfo i) {
        if (l.getAccountId() != null) {
            return l.getAccountId();
        }
        if ("SP".equals(i.type()) || "ATK".equals(i.type())) {
            return fin.consumptionAccount(i.type());
        }
        throw new BusinessException("GI_ACCOUNT", "Baris " + l.getLineNo() + " (" + i.code() + "): pilih akun beban");
    }

    @Override
    public void validateSubmit(GoodsIssue d) {
        if (d.getCostCenterId() == null) {
            throw new BusinessException("GI_CC", "Cost center penerima wajib diisi");
        }
        if (d.getLines().isEmpty()) {
            throw new BusinessException("GI_LINES", "Isi minimal satu baris");
        }
        for (GoodsIssue.Line l : d.getLines()) {
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            if ("FG".equals(i.type()) || "WIP".equals(i.type())) {
                throw new BusinessException("GI_ITEM", "Baris " + l.getLineNo() + ": barang jadi/WIP keluar lewat surat jalan atau pemusnahan");
            }
            if (i.lotTracked() && l.getLotId() == null) {
                throw new BusinessException("GI_LOT", "Baris " + l.getLineNo() + " (" + i.code() + "): pilih lot (FEFO)");
            }
            if (l.getLocationId() == null || l.getQty() == null || l.getQty().signum() <= 0) {
                throw new BusinessException("GI_LINE", "Baris " + l.getLineNo() + ": lokasi dan qty wajib diisi");
            }
            fin.validateAccount(accountOf(l, i), d.getCostCenterId(), "Baris " + l.getLineNo());
        }
    }

    @Override
    public void onPost(GoodsIssue d) {
        validateSubmit(d);
        List<JournalPostingService.IdLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (GoodsIssue.Line l : d.getLines()) {
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            BigDecimal cost = inventory.unitCost(l.getItemId(), l.getLotId(), l.getLocationId());
            inventory.move(new InventoryService.MoveCommand(MoveType.ISSUE, l.getItemId(), l.getLotId(), l.getLocationId(), null, l.getQty(),
                    cost, d.getDocDate(), "GI", d.getId(), d.getDocNo(), d.getPurpose()));
            l.setUnitCost(cost);
            l.setAmount(money(l.getQty().multiply(cost)));
            total = total.add(l.getAmount());
            String desc = "Pemakaian " + i.code() + " · " + d.getDocNo();
            lines.add(new JournalPostingService.IdLine(accountOf(l, i), d.getCostCenterId(), desc, l.getAmount(), null));
            lines.add(new JournalPostingService.IdLine(fin.inventoryAccount(i.type()), null, desc, null, l.getAmount()));
        }
        d.setTotal(total);
        journals.postIds(d.getPlantId(), d.getDocDate(), lines, "GI", d.getId(), d.getDocNo(), "Pengeluaran non-produksi " + d.getDocNo());
    }

    @Override
    public GoodsIssue reverse(GoodsIssue d, LocalDate date, String reason) {
        for (GoodsIssue.Line l : d.getLines()) {
            inventory.move(new InventoryService.MoveCommand(MoveType.RETURN, l.getItemId(), l.getLotId(), null, l.getLocationId(), l.getQty(),
                    l.getUnitCost(), date, "GI", d.getId(), d.getDocNo(), "Reversal: " + reason));
        }
        journals.reverseFor("GI", d.getId(), date, reason);
        return d;
    }
}

@RestController
@RequestMapping("/api/scm/goods-issues")
class GoodsIssueController extends DocumentApi<GoodsIssue> {

    private final ScmSupport scm;

    GoodsIssueController(GoodsIssueHandler handler, GoodsIssueRepository repo, Support support, ScmSupport scm) {
        super(handler, repo, support, GoodsIssue.class);
        this.scm = scm;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "purpose");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("total");
    }

    @Override
    protected void apply(GoodsIssue target, GoodsIssue in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (GoodsIssue.Line l : in.getLines()) {
            l.setId(null);
            l.setIssue(target);
            l.setLineNo(no++);
            target.getLines().add(l);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, GoodsIssue d) {
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                m.put("lotNo", m.get("lotId") == null ? null : scm.lotNo(((Number) m.get("lotId")).longValue()));
            }
        }
    }
}

// =====================================================================================================================
// SCM-26 Pengiriman & surat jalan
// =====================================================================================================================

@Component
class DeliveryHandler implements DocumentHandler<Delivery> {

    private final DeliveryRepository repo;
    private final SalesOrderRepository orders;
    private final InventoryService inventory;
    private final ScmSupport scm;
    private final FinApi fin;
    private final JournalPostingService journals;
    private final DocumentWorkflowService workflow;
    private final JdbcTemplate jdbc;

    DeliveryHandler(DeliveryRepository repo, SalesOrderRepository orders, InventoryService inventory, ScmSupport scm, FinApi fin,
                    JournalPostingService journals, @Lazy DocumentWorkflowService workflow, JdbcTemplate jdbc) {
        this.repo = repo;
        this.orders = orders;
        this.inventory = inventory;
        this.scm = scm;
        this.fin = fin;
        this.journals = journals;
        this.workflow = workflow;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "DO"; }
    @Override public String periodModule() { return "SCM"; }
    @Override public Delivery load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Surat jalan", id)); }
    @Override public Delivery save(Delivery doc) { return repo.save(doc); }
    @Override public boolean autoPost(Delivery doc) { return true; }
    @Override public BigDecimal amount(Delivery d) { return d.getTotalValue(); }

    @Override
    public String summary(Delivery d) {
        SalesOrder so = order(d);
        return "%s · %s%s".formatted(scm.partnerName(d.getPartnerId()), so.getDocNo(), d.isCreditHold() ? " · KREDIT TERTAHAN" : "");
    }

    SalesOrder order(Delivery d) {
        return orders.findById(d.getSoId()).orElseThrow(() -> new NotFoundException("Pesanan", d.getSoId()));
    }

    @Override
    public java.util.Set<String> approvalFlags(Delivery d) {
        return d.isCreditHold() ? java.util.Set.of("CREDIT_HOLD") : java.util.Set.of();
    }

    @Override
    public void validateSubmit(Delivery d) {
        SalesOrder so = order(d);
        if (so.getStatus() != DocStatus.APPROVED) {
            throw new BusinessException("DO_SO", "Pesanan " + so.getDocNo() + " belum dikonfirmasi atau sudah selesai");
        }
        if (d.getLines().isEmpty()) {
            throw new BusinessException("DO_LINES", "Isi minimal satu baris pengiriman");
        }
        Map<Long, BigDecimal> perLine = new LinkedHashMap<>();
        for (Delivery.Line l : d.getLines()) {
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            if (i.lotTracked() && (l.getLotId() == null || l.getLocationId() == null)) {
                throw new BusinessException("DO_LOT", "Baris " + l.getLineNo() + " (" + i.code() + "): pilih lot & lokasi");
            }
            if (l.getLocationId() != null && scm.isQuarantine(l.getLocationId())) {
                throw new BusinessException("DO_QRN", "Baris " + l.getLineNo() + ": tidak boleh mengirim dari lokasi karantina");
            }
            perLine.merge(l.getSoLineId(), l.getQty(), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> e : perLine.entrySet()) {
            SalesOrder.Line sl = so.getLines().stream().filter(x -> x.getId().equals(e.getKey())).findFirst()
                    .orElseThrow(() -> new BusinessException("DO_LINE", "Baris pesanan bukan milik pesanan ini"));
            BigDecimal remaining = sl.getQty().subtract(sl.getQtyShipped());
            if (sl.isClosed() || e.getValue().compareTo(remaining) > 0) {
                throw BusinessException.of("DO_OVER", "Qty kirim %s melebihi sisa pesanan %s", e.getValue().stripTrailingZeros().toPlainString(),
                        remaining.stripTrailingZeros().toPlainString());
            }
        }
        BigDecimal pending = jdbc.queryForObject("SELECT COALESCE(SUM(total), 0) FROM fin.ar_invoice WHERE partner_id = ? AND status = 'DRAFT'",
                BigDecimal.class, d.getPartnerId());
        BigDecimal gross = d.getTotalValue().add(so.isWithPpn() ? fin.ppn(d.getTotalValue()) : BigDecimal.ZERO);
        FinApi.CreditStatus c = fin.credit(d.getPartnerId(), gross, pending, d.getDocDate());
        d.setCreditHold(c.hold());
        d.setHoldReason(c.reason());
    }

    /** Barang keluar per lot (Released, belum kedaluwarsa), HPP, qty terkirim pesanan, draft faktur penjualan. */
    @Override
    public void onPost(Delivery d) {
        SalesOrder so = order(d);
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        BigDecimal cost = BigDecimal.ZERO;
        Map<Long, BigDecimal> shipped = new LinkedHashMap<>();
        for (Delivery.Line l : d.getLines()) {
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            BigDecimal unit = inventory.unitCost(l.getItemId(), l.getLotId(), l.getLocationId());
            inventory.move(new InventoryService.MoveCommand(MoveType.SHIP, l.getItemId(), l.getLotId(), l.getLocationId(), null, l.getQty(),
                    unit, d.getDocDate(), "DO", d.getId(), d.getDocNo(), "Kirim ke " + scm.partnerName(d.getPartnerId())));
            l.setUnitCost(unit);
            BigDecimal value = money(l.getQty().multiply(unit));
            cost = cost.add(value);
            byType.merge(i.type(), value, BigDecimal::add);
            shipped.merge(l.getSoLineId(), l.getQty(), BigDecimal::add);
        }
        d.setTotalCost(cost);
        scm.postInventory(d.getPlantId(), d.getDocDate(), byType, false, fin.accountId("5101"), null, "DO", d.getId(), d.getDocNo(),
                "HPP " + d.getDocNo() + " · " + scm.partnerName(d.getPartnerId()));

        List<FinApi.SalesLine> sales = new ArrayList<>();
        for (SalesOrder.Line sl : so.getLines()) {
            BigDecimal q = shipped.get(sl.getId());
            if (q != null) {
                sl.setQtyShipped(sl.getQtyShipped().add(q));
                sales.add(new FinApi.SalesLine(sl.getItemId(), null, q, sl.getUnitPrice(), sl.getDiscountPct()));
            }
        }
        orders.save(so);
        d.setArInvoiceId(fin.createSalesInvoice(d.getPlantId(), d.getDocDate(), d.getPartnerId(), d.getId(), d.getDocNo(), so.getCustomerPo(),
                so.isWithPpn(), sales));
        if (so.getLines().stream().allMatch(sl -> sl.isClosed() || sl.getQtyShipped().compareTo(sl.getQty()) >= 0)) {
            workflow.markDone("SO", so.getId(), "Terkirim penuh dengan " + d.getDocNo());
        }
    }

    @Override
    public Delivery reverse(Delivery d, LocalDate date, String reason) {
        if (d.getArInvoiceId() != null) {
            String st = jdbc.queryForObject("SELECT status || ':' || doc_no FROM fin.ar_invoice WHERE id = ?", String.class, d.getArInvoiceId());
            if (!st.startsWith("CANCELLED")) {
                throw new BusinessException("DO_INVOICED", "Batalkan dulu faktur penjualan " + st.substring(st.indexOf(':') + 1) + " (FIN-20)");
            }
        }
        for (Delivery.Line l : d.getLines()) {
            inventory.move(new InventoryService.MoveCommand(MoveType.RETURN, l.getItemId(), l.getLotId(), null, l.getLocationId(), l.getQty(),
                    l.getUnitCost(), date, "DO", d.getId(), d.getDocNo(), "Reversal: " + reason));
        }
        journals.reverseFor("DO", d.getId(), date, reason);
        SalesOrder so = order(d);
        for (Delivery.Line l : d.getLines()) {
            so.getLines().stream().filter(sl -> sl.getId().equals(l.getSoLineId())).findFirst()
                    .ifPresent(sl -> sl.setQtyShipped(sl.getQtyShipped().subtract(l.getQty())));
        }
        if (so.getStatus() == DocStatus.DONE) {
            so.setStatus(DocStatus.APPROVED);
        }
        orders.save(so);
        return d;
    }
}

@RestController
@RequestMapping("/api/scm/deliveries")
class DeliveryController extends DocumentApi<Delivery> {

    private final DeliveryHandler handler;
    private final InventoryService inventory;
    private final ScmSupport scm;
    private final JdbcTemplate jdbc;

    DeliveryController(DeliveryHandler handler, DeliveryRepository repo, Support support, InventoryService inventory, ScmSupport scm,
                       JdbcTemplate jdbc) {
        super(handler, repo, support, Delivery.class);
        this.handler = handler;
        this.inventory = inventory;
        this.scm = scm;
        this.jdbc = jdbc;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "vehicleNo", "driver", "notes");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("partnerId", "creditHold", "holdReason", "totalValue", "totalCost", "arInvoiceId");
    }

    @Override
    protected void apply(Delivery target, Delivery in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        for (Delivery.Line l : in.getLines()) {
            l.setId(null);
            l.setDelivery(target);
            target.getLines().add(l);
        }
    }

    /** Baris tanpa lot dipecah otomatis menurut FEFO dari gudang barang jadi. */
    @Override
    protected void beforeSave(Delivery d, boolean isNew) {
        require(d.getSoId() != null, "DO_SO", "Pilih pesanan pelanggan");
        SalesOrder so = handler.order(d);
        d.setPartnerId(so.getPartnerId());
        if (d.getShipTo() == null || d.getShipTo().isBlank()) {
            d.setShipTo(so.getShipTo());
        }
        Long plant = UserContext.plantId();
        List<Delivery.Line> out = new ArrayList<>();
        for (Delivery.Line l : d.getLines()) {
            require(l.getSoLineId() != null && l.getQty() != null && l.getQty().signum() > 0, "DO_LINE", "Setiap baris wajib merujuk baris pesanan dan qty > 0");
            SalesOrder.Line sl = so.getLines().stream().filter(x -> x.getId().equals(l.getSoLineId())).findFirst()
                    .orElseThrow(() -> new BusinessException("DO_LINE", "Baris pesanan bukan milik pesanan ini"));
            l.setItemId(sl.getItemId());
            l.setUnitPrice(ScmSupport.net(BigDecimal.ONE, sl.getUnitPrice(), sl.getDiscountPct()));
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            if (i.lotTracked() && l.getLotId() == null) {
                for (InventoryService.FefoPick p : inventory.suggestFefo(l.getItemId(), l.getQty(), scm.fgWarehouse(plant), d.getDocDate())) {
                    Delivery.Line x = new Delivery.Line();
                    x.setDelivery(d);
                    x.setSoLineId(l.getSoLineId());
                    x.setItemId(l.getItemId());
                    x.setLotId(p.lotId());
                    x.setLocationId(p.locationId());
                    x.setQty(p.qty());
                    x.setUnitPrice(l.getUnitPrice());
                    out.add(x);
                }
            } else {
                out.add(l);
            }
        }
        d.getLines().clear();
        short no = 1;
        BigDecimal total = BigDecimal.ZERO;
        for (Delivery.Line l : out) {
            l.setLineNo(no++);
            total = total.add(money(l.getQty().multiply(l.getUnitPrice())));
            d.getLines().add(l);
        }
        d.setTotalValue(total);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, Delivery d) {
        body.put("partnerName", scm.partnerName(d.getPartnerId()));
        body.put("soNo", jdbc.queryForList("SELECT doc_no FROM scm.so WHERE id = ?", String.class, d.getSoId()).stream().findFirst().orElse(null));
        if (d.getArInvoiceId() != null) {
            body.put("arInvoiceNo", jdbc.queryForObject("SELECT doc_no FROM fin.ar_invoice WHERE id = ?", String.class, d.getArInvoiceId()));
        }
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                m.put("itemLabel", scm.itemLabel(((Number) m.get("itemId")).longValue()));
                m.put("lotNo", m.get("lotId") == null ? null : scm.lotNo(((Number) m.get("lotId")).longValue()));
                m.put("expDate", m.get("lotId") == null ? null : jdbc.queryForObject("SELECT exp_date FROM scm.lot WHERE id = ?",
                        java.sql.Date.class, ((Number) m.get("lotId")).longValue()));
                m.put("locationLabel", m.get("locationId") == null ? null : scm.locationLabel(((Number) m.get("locationId")).longValue()));
            }
        }
    }
}

// =====================================================================================================================
// SCM-27 Retur pelanggan
// =====================================================================================================================

@Component
class CustomerReturnHandler implements DocumentHandler<CustomerReturn> {

    private final CustomerReturnRepository repo;
    private final DeliveryRepository deliveries;
    private final InventoryService inventory;
    private final ScmSupport scm;
    private final FinApi fin;
    private final JdbcTemplate jdbc;

    CustomerReturnHandler(CustomerReturnRepository repo, DeliveryRepository deliveries, InventoryService inventory, ScmSupport scm, FinApi fin,
                          JdbcTemplate jdbc) {
        this.repo = repo;
        this.deliveries = deliveries;
        this.inventory = inventory;
        this.scm = scm;
        this.fin = fin;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "CRT"; }
    @Override public String periodModule() { return "SCM"; }
    @Override public CustomerReturn load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Retur pelanggan", id)); }
    @Override public CustomerReturn save(CustomerReturn doc) { return repo.save(doc); }
    @Override public boolean autoPost(CustomerReturn doc) { return true; }
    @Override public BigDecimal amount(CustomerReturn d) { return d.getTotal(); }

    @Override
    public boolean allows(String action, CustomerReturn doc) {
        return !"REVERSE".equals(action);
    }

    Delivery delivery(CustomerReturn d) {
        return deliveries.findById(d.getDeliveryId()).orElseThrow(() -> new NotFoundException("Surat jalan", d.getDeliveryId()));
    }

    @Override
    public String summary(CustomerReturn d) {
        return scm.partnerName(d.getPartnerId()) + " · " + delivery(d).getDocNo() + (d.getReason() == null ? "" : " · " + d.getReason());
    }

    @Override
    public void validateSubmit(CustomerReturn d) {
        Delivery dl = delivery(d);
        if (dl.getStatus() != DocStatus.POSTED) {
            throw new BusinessException("CRT_DO", "Surat jalan " + dl.getDocNo() + " belum diposting atau sudah dibatalkan");
        }
        if (dl.getArInvoiceId() == null || !"POSTED".equals(jdbc.queryForObject("SELECT status FROM fin.ar_invoice WHERE id = ?", String.class,
                dl.getArInvoiceId()))) {
            throw new BusinessException("CRT_INVOICE", "Faktur penjualan surat jalan ini belum diposting (FIN-20); nota kredit butuh faktur terposting");
        }
        if (d.getReason() == null || d.getReason().isBlank()) {
            throw new BusinessException("CRT_REASON", "Alasan retur wajib diisi");
        }
        if (d.getLines().isEmpty()) {
            throw new BusinessException("CRT_LINES", "Isi minimal satu baris");
        }
        for (CustomerReturn.Line l : d.getLines()) {
            BigDecimal returned = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(l.qty), 0) FROM scm.customer_return_line l JOIN scm.customer_return r ON r.id = l.return_id
                    WHERE l.delivery_line_id = ? AND r.id <> ? AND r.status NOT IN ('CANCELLED','REJECTED')""",
                    BigDecimal.class, l.getDeliveryLineId(), d.getId());
            Delivery.Line src = dl.getLines().stream().filter(x -> x.getId().equals(l.getDeliveryLineId())).findFirst()
                    .orElseThrow(() -> new BusinessException("CRT_LINE", "Baris bukan dari surat jalan ini"));
            if (l.getQty().add(returned).compareTo(src.getQty()) > 0) {
                throw BusinessException.of("CRT_OVER", "Baris %d: retur melebihi qty terkirim (%s, sudah diretur %s)", l.getLineNo(),
                        src.getQty().stripTrailingZeros().toPlainString(), returned.stripTrailingZeros().toPlainString());
            }
            if (!scm.isQuarantine(l.getLocationId())) {
                throw new BusinessException("CRT_QRN", "Barang retur wajib masuk lokasi karantina menunggu keputusan QA");
            }
        }
    }

    /** Barang masuk karantina (Hold), HPP dibalik, nota kredit mengurangi piutang faktur surat jalan. */
    @Override
    public void onPost(CustomerReturn d) {
        validateSubmit(d);
        Delivery dl = delivery(d);
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        for (CustomerReturn.Line l : d.getLines()) {
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            inventory.move(new InventoryService.MoveCommand(MoveType.RETURN, l.getItemId(), l.getLotId(), null, l.getLocationId(), l.getQty(),
                    l.getUnitCost(), d.getDocDate(), "CRT", d.getId(), d.getDocNo(), "Retur pelanggan: " + d.getReason()));
            byType.merge(i.type(), money(l.getQty().multiply(l.getUnitCost())), BigDecimal::add);
        }
        scm.postInventory(d.getPlantId(), d.getDocDate(), byType, true, fin.accountId("5101"), null, "CRT", d.getId(), d.getDocNo(),
                "Retur " + d.getDocNo() + " · " + scm.partnerName(d.getPartnerId()));
        fin.applySalesCreditNote(d.getPlantId(), d.getDocDate(), dl.getArInvoiceId(), d.getSubtotal(), d.getPpnAmount(), "CRT", d.getId(),
                d.getDocNo());
    }
}

@RestController
@RequestMapping("/api/scm/customer-returns")
class CustomerReturnController extends DocumentApi<CustomerReturn> {

    private final CustomerReturnHandler handler;
    private final InventoryService inventory;
    private final ScmSupport scm;
    private final FinApi fin;
    private final JdbcTemplate jdbc;

    CustomerReturnController(CustomerReturnHandler handler, CustomerReturnRepository repo, Support support, InventoryService inventory,
                             ScmSupport scm, FinApi fin, JdbcTemplate jdbc) {
        super(handler, repo, support, CustomerReturn.class);
        this.handler = handler;
        this.inventory = inventory;
        this.scm = scm;
        this.fin = fin;
        this.jdbc = jdbc;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "reason");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("partnerId", "subtotal", "ppnAmount", "total", "withPpn");
    }

    @Override
    protected void apply(CustomerReturn target, CustomerReturn in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (CustomerReturn.Line l : in.getLines()) {
            l.setId(null);
            l.setCustomerReturn(target);
            l.setLineNo(no++);
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(CustomerReturn d, boolean isNew) {
        require(d.getDeliveryId() != null, "CRT_DO", "Pilih surat jalan");
        Delivery dl = handler.delivery(d);
        d.setPartnerId(dl.getPartnerId());
        d.setWithPpn(Boolean.TRUE.equals(jdbc.queryForObject("SELECT with_ppn FROM scm.so WHERE id = ?", Boolean.class, dl.getSoId())));
        Long plant = UserContext.plantId();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CustomerReturn.Line l : d.getLines()) {
            require(l.getDeliveryLineId() != null && l.getQty() != null && l.getQty().signum() > 0, "CRT_LINE",
                    "Setiap baris wajib merujuk baris surat jalan dan qty > 0");
            Delivery.Line src = dl.getLines().stream().filter(x -> x.getId().equals(l.getDeliveryLineId())).findFirst()
                    .orElseThrow(() -> new BusinessException("CRT_LINE", "Baris bukan dari surat jalan ini"));
            l.setItemId(src.getItemId());
            l.setLotId(src.getLotId());
            l.setUnitPrice(src.getUnitPrice());
            l.setUnitCost(src.getUnitCost());
            if (l.getLocationId() == null) {
                l.setLocationId(inventory.quarantineLocation(plant, inventory.requireItem(src.getItemId()).type()));
            }
            l.setAmount(money(l.getQty().multiply(l.getUnitPrice())));
            subtotal = subtotal.add(l.getAmount());
        }
        d.setSubtotal(subtotal);
        d.setPpnAmount(d.isWithPpn() ? fin.ppn(subtotal) : BigDecimal.ZERO);
        d.setTotal(subtotal.add(d.getPpnAmount()));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, CustomerReturn d) {
        body.put("partnerName", scm.partnerName(d.getPartnerId()));
        body.put("deliveryNo", jdbc.queryForList("SELECT doc_no FROM scm.delivery WHERE id = ?", String.class, d.getDeliveryId())
                .stream().findFirst().orElse(null));
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                m.put("itemLabel", scm.itemLabel(((Number) m.get("itemId")).longValue()));
                m.put("lotNo", m.get("lotId") == null ? null : scm.lotNo(((Number) m.get("lotId")).longValue()));
            }
        }
    }
}
