package id.herbatech.erp.prc;

import id.herbatech.erp.fin.FinApi;
import id.herbatech.erp.fin.JournalPostingService;
import id.herbatech.erp.scm.InventoryService;
import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.domain.DocStatus;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.meta.DashboardProvider;
import id.herbatech.erp.shared.meta.KpiProvider;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import id.herbatech.erp.shared.web.LookupSource;
import id.herbatech.erp.shared.web.MasterController;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static id.herbatech.erp.prc.PrcSupport.money;

interface ServiceAcceptanceRepository extends DocumentRepository<ServiceAcceptance> {
}

interface LandedCostRepository extends DocumentRepository<LandedCost> {
}

interface SupplierReturnRepository extends DocumentRepository<SupplierReturn> {
}

// =====================================================================================================================
// PRC-12 Jasa & BAST
// =====================================================================================================================

@Component
class ServiceAcceptanceHandler implements DocumentHandler<ServiceAcceptance> {

    private final ServiceAcceptanceRepository repo;
    private final PurchaseOrderRepository orders;
    private final PurchaseOrderHandler poHandler;
    private final PrcSupport prc;
    private final FinApi fin;
    private final JournalPostingService journals;

    ServiceAcceptanceHandler(ServiceAcceptanceRepository repo, PurchaseOrderRepository orders, PurchaseOrderHandler poHandler, PrcSupport prc,
                             FinApi fin, JournalPostingService journals) {
        this.repo = repo;
        this.orders = orders;
        this.poHandler = poHandler;
        this.prc = prc;
        this.fin = fin;
        this.journals = journals;
    }

    @Override public String docType() { return "BAST"; }
    @Override public String periodModule() { return "AP"; }
    @Override public ServiceAcceptance load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("BAST", id)); }
    @Override public ServiceAcceptance save(ServiceAcceptance doc) { return repo.save(doc); }
    @Override public boolean autoPost(ServiceAcceptance doc) { return true; }
    @Override public BigDecimal amount(ServiceAcceptance d) { return d.getTotal(); }

    @Override
    public boolean allows(String action, ServiceAcceptance doc) {
        return !"REVERSE".equals(action);
    }

    PurchaseOrder po(ServiceAcceptance d) {
        return orders.findById(d.getPoId()).orElseThrow(() -> new NotFoundException("PO", d.getPoId()));
    }

    @Override
    public String summary(ServiceAcceptance d) {
        PurchaseOrder po = po(d);
        return "%s · %s".formatted(prc.partnerName(po.getPartnerId()), po.getDocNo());
    }

    PurchaseOrder.Line poLine(PurchaseOrder po, Long lineId) {
        return po.getLines().stream().filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new BusinessException("BAST_LINE", "Baris PO bukan milik PO ini"));
    }

    @Override
    public void validateSubmit(ServiceAcceptance d) {
        PurchaseOrder po = po(d);
        if (po.getStatus() != DocStatus.APPROVED) {
            throw new BusinessException("BAST_PO", "PO " + po.getDocNo() + " belum terbit atau sudah selesai");
        }
        if (d.getLines().isEmpty()) {
            throw new BusinessException("BAST_LINES", "Isi minimal satu baris jasa yang diterima");
        }
        for (ServiceAcceptance.Line l : d.getLines()) {
            PurchaseOrder.Line pl = poLine(po, l.getPoLineId());
            if (!"SVC".equals(prc.item(pl.getItemId()).get("type"))) {
                throw new BusinessException("BAST_ITEM", "Baris " + l.getLineNo() + ": BAST hanya untuk jasa; barang diterima lewat SCM-20");
            }
            if (l.getQty().compareTo(pl.getQty().subtract(pl.getReceivedQty())) > 0) {
                throw new BusinessException("BAST_OVER", "Baris " + l.getLineNo() + ": melebihi sisa jasa di PO");
            }
        }
    }

    /** Beban jasa diakui ke cost center PO (lawan: hutang belum difakturkan), komitmen anggaran terpakai. */
    @Override
    public void onPost(ServiceAcceptance d) {
        validateSubmit(d);
        PurchaseOrder po = po(d);
        List<JournalPostingService.IdLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        String desc = "BAST " + d.getDocNo() + " · " + prc.partnerName(po.getPartnerId());
        for (ServiceAcceptance.Line l : d.getLines()) {
            PurchaseOrder.Line pl = poLine(po, l.getPoLineId());
            BigDecimal amount = money(PrcSupport.net(l.getQty(), pl.getUnitPrice(), pl.getDiscountPct()).multiply(po.getExchangeRate()));
            l.setAmount(amount);
            total = total.add(amount);
            lines.add(new JournalPostingService.IdLine(pl.getAccountId(), pl.getCostCenterId(), desc, amount, null));
            poHandler.receive(po, pl, l.getQty(), amount, d.getDocDate());
        }
        lines.add(new JournalPostingService.IdLine(fin.accountId("2102"), null, desc, null, total));
        d.setTotal(total);
        journals.postIds(d.getPlantId(), d.getDocDate(), lines, "BAST", d.getId(), d.getDocNo(), desc);
        poHandler.save(po);
        poHandler.closeIfComplete(po, "Jasa diterima penuh (" + d.getDocNo() + ")");
    }
}

@RestController
@RequestMapping("/api/prc/service-acceptances")
class ServiceAcceptanceController extends DocumentApi<ServiceAcceptance> {

    private final ServiceAcceptanceHandler handler;
    private final PrcSupport prc;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;

    ServiceAcceptanceController(ServiceAcceptanceHandler handler, ServiceAcceptanceRepository repo, Support support, PrcSupport prc,
                                JdbcTemplate jdbc, PermissionService perm) {
        super(handler, repo, support, ServiceAcceptance.class);
        this.handler = handler;
        this.prc = prc;
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("total");
    }

    @Override
    protected void apply(ServiceAcceptance target, ServiceAcceptance in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (ServiceAcceptance.Line l : in.getLines()) {
            l.setId(null);
            l.setAcceptance(target);
            l.setLineNo(no++);
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(ServiceAcceptance d, boolean isNew) {
        require(d.getPoId() != null, "BAST_PO", "Pilih PO jasa");
        PurchaseOrder po = handler.po(d);
        BigDecimal total = BigDecimal.ZERO;
        for (ServiceAcceptance.Line l : d.getLines()) {
            require(l.getPoLineId() != null && l.getQty() != null && l.getQty().signum() > 0, "BAST_LINE", "Setiap baris wajib merujuk baris PO dan qty > 0");
            PurchaseOrder.Line pl = handler.poLine(po, l.getPoLineId());
            l.setAmount(money(PrcSupport.net(l.getQty(), pl.getUnitPrice(), pl.getDiscountPct()).multiply(po.getExchangeRate())));
            total = total.add(l.getAmount());
        }
        d.setTotal(total);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, ServiceAcceptance d) {
        PurchaseOrder po = handler.po(d);
        body.put("poNo", po.getDocNo());
        body.put("partnerName", prc.partnerName(po.getPartnerId()));
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                PurchaseOrder.Line pl = handler.poLine(po, ((Number) m.get("poLineId")).longValue());
                m.put("itemLabel", prc.itemLabel(pl.getItemId()));
                m.put("ordered", pl.getQty());
                m.put("received", pl.getReceivedQty());
            }
        }
    }

    /** Baris jasa PO yang belum diterima penuh. */
    @GetMapping("/po-lines")
    public List<Map<String, Object>> poLines(@RequestParam Long poId) {
        perm.require("PRC-12", Action.VIEW);
        return jdbc.queryForList("""
                SELECT l.id AS po_line_id, l.item_id, i.code || ' · ' || i.name AS item_label, COALESCE(l.description, '') AS description,
                       l.qty, l.received_qty, l.qty - l.received_qty AS remaining, l.unit_price
                FROM prc.po_line l JOIN prc.po p ON p.id = l.po_id JOIN sys.item i ON i.id = l.item_id
                WHERE l.po_id = ? AND p.status = 'APPROVED' AND i.type = 'SVC' AND NOT l.closed AND l.qty > l.received_qty ORDER BY l.line_no""", poId);
    }
}

// =====================================================================================================================
// PRC-10 Impor & landed cost
// =====================================================================================================================

@Component
class LandedCostHandler implements DocumentHandler<LandedCost> {

    static final Set<String> TAX_KINDS = Set.of("PPN_IMPORT", "PPH22_IMPORT");

    private final LandedCostRepository repo;
    private final InventoryService inventory;
    private final FinApi fin;
    private final JournalPostingService journals;
    private final PrcSupport prc;
    private final JdbcTemplate jdbc;

    LandedCostHandler(LandedCostRepository repo, InventoryService inventory, FinApi fin, JournalPostingService journals, PrcSupport prc,
                      JdbcTemplate jdbc) {
        this.repo = repo;
        this.inventory = inventory;
        this.fin = fin;
        this.journals = journals;
        this.prc = prc;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "LC"; }
    @Override public String periodModule() { return "COST"; }
    @Override public LandedCost load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Landed cost", id)); }
    @Override public LandedCost save(LandedCost doc) { return repo.save(doc); }
    @Override public boolean autoPost(LandedCost doc) { return true; }
    @Override public BigDecimal amount(LandedCost d) { return d.getTotal(); }

    @Override
    public boolean allows(String action, LandedCost doc) {
        return !"REVERSE".equals(action);
    }

    @Override
    public String summary(LandedCost d) {
        String po = jdbc.queryForList("SELECT doc_no FROM prc.po WHERE id = ?", String.class, d.getPoId()).stream().findFirst().orElse("?");
        return "%s%s%s".formatted(po, d.getPibNo() == null ? "" : " · PIB " + d.getPibNo(), d.getShipmentNo() == null ? "" : " · " + d.getShipmentNo());
    }

    /** Biaya yang dikapitalisasi dialokasikan ke baris GR terposting PO ini menurut nilai atau qty; pajak impor tidak dialokasikan. */
    void allocate(LandedCost d) {
        BigDecimal cap = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (LandedCost.Charge c : d.getCharges()) {
            if (TAX_KINDS.contains(c.getKind())) {
                tax = tax.add(c.getAmount());
            } else {
                cap = cap.add(c.getAmount());
            }
        }
        d.setCapitalized(cap);
        d.setTaxCredit(tax);
        d.setTotal(cap.add(tax));
        d.getAllocations().clear();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT gl.id, gl.item_id, gl.lot_id, gl.qty, gl.amount FROM scm.gr_line gl JOIN scm.gr g ON g.id = gl.gr_id
                WHERE g.po_id = ? AND g.status = 'POSTED' ORDER BY gl.id""", d.getPoId());
        BigDecimal base = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) {
            base = base.add((BigDecimal) ("QTY".equals(d.getAllocBasis()) ? r.get("qty") : r.get("amount")));
        }
        BigDecimal left = cap;
        short no = 1;
        for (int k = 0; k < rows.size(); k++) {
            Map<String, Object> r = rows.get(k);
            BigDecimal b = (BigDecimal) ("QTY".equals(d.getAllocBasis()) ? r.get("qty") : r.get("amount"));
            BigDecimal share = k == rows.size() - 1 || base.signum() == 0 ? left : money(cap.multiply(b).divide(base, 10, RoundingMode.HALF_UP));
            left = left.subtract(share);
            LandedCost.Allocation a = new LandedCost.Allocation();
            a.setLandedCost(d);
            a.setLineNo(no++);
            a.setGrLineId(((Number) r.get("id")).longValue());
            a.setItemId(((Number) r.get("item_id")).longValue());
            a.setLotId(r.get("lot_id") == null ? null : ((Number) r.get("lot_id")).longValue());
            a.setQty((BigDecimal) r.get("qty"));
            a.setBaseValue(money((BigDecimal) r.get("amount")));
            a.setAllocated(share);
            d.getAllocations().add(a);
        }
    }

    @Override
    public void validateSubmit(LandedCost d) {
        String st = jdbc.queryForObject("SELECT status FROM prc.po WHERE id = ?", String.class, d.getPoId());
        if (!"APPROVED".equals(st) && !"DONE".equals(st)) {
            throw new BusinessException("LC_PO", "PO belum terbit");
        }
        if (d.getCharges().isEmpty() || d.getTotal().signum() <= 0) {
            throw new BusinessException("LC_CHARGES", "Isi minimal satu komponen biaya");
        }
        allocate(d);
        if (d.getCapitalized().signum() > 0 && d.getAllocations().isEmpty()) {
            throw new BusinessException("LC_GR", "Belum ada penerimaan barang (SCM-20) terposting untuk PO ini");
        }
    }

    /** Harga pokok lot naik untuk sisa stok; porsi yang sudah terpakai ke selisih harga bahan. PPN & PPh 22 impor jadi kredit pajak. */
    @Override
    public void onPost(LandedCost d) {
        validateSubmit(d);
        List<JournalPostingService.IdLine> lines = new ArrayList<>();
        String desc = "Landed cost " + d.getDocNo();
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        BigDecimal variance = BigDecimal.ZERO;
        for (LandedCost.Allocation a : d.getAllocations()) {
            BigDecimal capitalized = inventory.revalueLot(a.getItemId(), a.getLotId(), a.getQty(), a.getAllocated());
            byType.merge(inventory.requireItem(a.getItemId()).type(), capitalized, BigDecimal::add);
            variance = variance.add(a.getAllocated().subtract(capitalized));
        }
        byType.forEach((type, v) -> lines.add(new JournalPostingService.IdLine(fin.inventoryAccount(type), null, desc + " · " + type, v, null)));
        lines.add(new JournalPostingService.IdLine(fin.accountId("5201"), null, desc + " · stok sudah terpakai", variance, null));
        for (LandedCost.Charge c : d.getCharges()) {
            if ("PPN_IMPORT".equals(c.getKind())) {
                lines.add(new JournalPostingService.IdLine(fin.accountId("1220"), null, "PPN impor " + d.getPibNo(), c.getAmount(), null));
            } else if ("PPH22_IMPORT".equals(c.getKind())) {
                lines.add(new JournalPostingService.IdLine(fin.accountId("1225"), null, "PPh 22 impor " + d.getPibNo(), c.getAmount(), null));
            }
        }
        lines.add(new JournalPostingService.IdLine(fin.accountId("2109"), null, desc, null, d.getTotal()));
        journals.postIds(d.getPlantId(), d.getDocDate(), lines, "LC", d.getId(), d.getDocNo(), desc);
    }
}

@RestController
@RequestMapping("/api/prc/landed-costs")
class LandedCostController extends DocumentApi<LandedCost> {

    private final LandedCostHandler handler;
    private final PrcSupport prc;
    private final JdbcTemplate jdbc;

    LandedCostController(LandedCostHandler handler, LandedCostRepository repo, Support support, PrcSupport prc, JdbcTemplate jdbc) {
        super(handler, repo, support, LandedCost.class);
        this.handler = handler;
        this.prc = prc;
        this.jdbc = jdbc;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "shipmentNo", "blNo", "pibNo");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("capitalized", "taxCredit", "total");
    }

    @Override
    protected void apply(LandedCost target, LandedCost in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getCharges().clear();
        short no = 1;
        for (LandedCost.Charge c : in.getCharges()) {
            c.setId(null);
            c.setLandedCost(target);
            c.setLineNo(no++);
            target.getCharges().add(c);
        }
    }

    @Override
    protected void beforeSave(LandedCost d, boolean isNew) {
        require(d.getPoId() != null, "LC_PO", "Pilih PO impor");
        for (LandedCost.Charge c : d.getCharges()) {
            require(c.getKind() != null && c.getAmount() != null && c.getAmount().signum() >= 0, "LC_CHARGE", "Setiap biaya wajib berisi jenis & nilai");
        }
        handler.allocate(d);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, LandedCost d) {
        body.put("poNo", jdbc.queryForList("SELECT doc_no FROM prc.po WHERE id = ?", String.class, d.getPoId()).stream().findFirst().orElse(null));
        if (body.get("allocations") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                m.put("itemLabel", prc.itemLabel(((Number) m.get("itemId")).longValue()));
                m.put("lotNo", m.get("lotId") == null ? null : jdbc.queryForObject("SELECT lot_no FROM scm.lot WHERE id = ?", String.class,
                        ((Number) m.get("lotId")).longValue()));
            }
        }
    }
}

// =====================================================================================================================
// PRC-11 / SCM-28 Retur & klaim supplier
// =====================================================================================================================

@Component
class SupplierReturnHandler implements DocumentHandler<SupplierReturn> {

    private final SupplierReturnRepository repo;
    private final PurchaseOrderRepository orders;
    private final PurchaseOrderHandler poHandler;
    private final InventoryService inventory;
    private final PrcSupport prc;
    private final FinApi fin;
    private final JournalPostingService journals;
    private final JdbcTemplate jdbc;

    SupplierReturnHandler(SupplierReturnRepository repo, PurchaseOrderRepository orders, PurchaseOrderHandler poHandler,
                          InventoryService inventory, PrcSupport prc, FinApi fin, JournalPostingService journals, JdbcTemplate jdbc) {
        this.repo = repo;
        this.orders = orders;
        this.poHandler = poHandler;
        this.inventory = inventory;
        this.prc = prc;
        this.fin = fin;
        this.journals = journals;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "RTS"; }
    @Override public String periodModule() { return "SCM"; }
    @Override public SupplierReturn load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Retur supplier", id)); }
    @Override public SupplierReturn save(SupplierReturn doc) { return repo.save(doc); }
    @Override public boolean autoPost(SupplierReturn doc) { return true; }
    @Override public BigDecimal amount(SupplierReturn d) { return d.getTotal(); }

    @Override
    public boolean allows(String action, SupplierReturn doc) {
        return !"REVERSE".equals(action);
    }

    @Override
    public String summary(SupplierReturn d) {
        return prc.partnerName(d.getPartnerId()) + " · " + ("REPLACE".equals(d.getClaimType()) ? "minta ganti" : "nota debet")
                + (d.getReason() == null ? "" : " · " + d.getReason());
    }

    @Override
    public void validateSubmit(SupplierReturn d) {
        if (d.getReason() == null || d.getReason().isBlank()) {
            throw new BusinessException("RTS_REASON", "Alasan retur wajib diisi (mis. hasil QC reject)");
        }
        if (d.getLines().isEmpty()) {
            throw new BusinessException("RTS_LINES", "Isi minimal satu baris");
        }
        for (SupplierReturn.Line l : d.getLines()) {
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            if (i.lotTracked() && l.getLotId() == null) {
                throw new BusinessException("RTS_LOT", "Baris " + l.getLineNo() + " (" + i.code() + "): pilih lot");
            }
            BigDecimal have = inventory.onHand(l.getItemId(), l.getLotId(), l.getLocationId());
            if (l.getQty() == null || l.getQty().signum() <= 0 || have.compareTo(l.getQty()) < 0) {
                throw BusinessException.of("RTS_QTY", "Baris %d: qty harus > 0 dan ≤ stok di lokasi (%s)", l.getLineNo(),
                        have.stripTrailingZeros().toPlainString());
            }
            if (l.getPoLineId() != null) {
                BigDecimal invoiced = jdbc.queryForObject("""
                        SELECT COALESCE(SUM(il.qty), 0) FROM fin.ap_invoice_line il JOIN fin.ap_invoice i ON i.id = il.invoice_id
                        WHERE il.po_line_id = ? AND i.status IN ('SUBMITTED','APPROVED','POSTED')""", BigDecimal.class, l.getPoLineId());
                BigDecimal received = jdbc.queryForObject("SELECT received_qty FROM prc.po_line WHERE id = ?", BigDecimal.class, l.getPoLineId());
                if (invoiced.compareTo(received.subtract(l.getQty())) > 0) {
                    throw new BusinessException("RTS_INVOICED", "Baris " + l.getLineNo() + ": barang sudah difakturkan supplier; minta nota kredit "
                            + "supplier dan catat koreksinya di FIN-10");
                }
            }
        }
    }

    /** Barang keluar ke supplier (status lot apa pun), hutang belum difakturkan berkurang, qty diterima PO dikoreksi. */
    @Override
    public void onPost(SupplierReturn d) {
        validateSubmit(d);
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        PurchaseOrder po = d.getPoId() == null ? null : orders.findById(d.getPoId()).orElse(null);
        for (SupplierReturn.Line l : d.getLines()) {
            BigDecimal cost = inventory.unitCost(l.getItemId(), l.getLotId(), l.getLocationId());
            inventory.move(new InventoryService.MoveCommand(InventoryService.MoveType.RETURN, l.getItemId(), l.getLotId(), l.getLocationId(), null,
                    l.getQty(), cost, d.getDocDate(), "RTS", d.getId(), d.getDocNo(), d.getReason()));
            l.setUnitCost(cost);
            l.setAmount(money(l.getQty().multiply(cost)));
            total = total.add(l.getAmount());
            byType.merge(inventory.requireItem(l.getItemId()).type(), l.getAmount(), BigDecimal::add);
            if (po != null && l.getPoLineId() != null) {
                po.getLines().stream().filter(x -> x.getId().equals(l.getPoLineId())).findFirst().ifPresent(x -> {
                    x.setReceivedQty(x.getReceivedQty().subtract(l.getQty()));
                    if ("DEBIT_NOTE".equals(d.getClaimType())) {
                        x.setClosed(true);
                    }
                });
            }
        }
        d.setTotal(total);
        List<JournalPostingService.IdLine> lines = new ArrayList<>();
        String desc = "Retur supplier " + d.getDocNo() + " · " + prc.partnerName(d.getPartnerId());
        lines.add(new JournalPostingService.IdLine(fin.accountId("2102"), null, desc, total, null));
        byType.forEach((t, v) -> lines.add(new JournalPostingService.IdLine(fin.inventoryAccount(t), null, desc + " · " + t, null, v)));
        journals.postIds(d.getPlantId(), d.getDocDate(), lines, "RTS", d.getId(), d.getDocNo(), desc);
        if (po != null) {
            if ("REPLACE".equals(d.getClaimType()) && po.getStatus() == DocStatus.DONE) {
                po.setStatus(DocStatus.APPROVED);
            }
            poHandler.save(po);
            poHandler.closeIfComplete(po, "Selesai setelah retur " + d.getDocNo());
        }
    }
}

@RestController
@RequestMapping("/api/prc/supplier-returns")
class SupplierReturnController extends DocumentApi<SupplierReturn> {

    private final PrcSupport prc;
    private final InventoryService inventory;
    private final JdbcTemplate jdbc;

    SupplierReturnController(SupplierReturnHandler handler, SupplierReturnRepository repo, Support support, PrcSupport prc,
                             InventoryService inventory, JdbcTemplate jdbc) {
        super(handler, repo, support, SupplierReturn.class);
        this.prc = prc;
        this.inventory = inventory;
        this.jdbc = jdbc;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "reason", "debitNoteNo");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("total");
    }

    @Override
    protected void apply(SupplierReturn target, SupplierReturn in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (SupplierReturn.Line l : in.getLines()) {
            l.setId(null);
            l.setSupplierReturn(target);
            l.setLineNo(no++);
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(SupplierReturn d, boolean isNew) {
        if (d.getPoId() != null) {
            d.setPartnerId(jdbc.queryForObject("SELECT partner_id FROM prc.po WHERE id = ?", Long.class, d.getPoId()));
        }
        require(d.getPartnerId() != null, "RTS_PARTNER", "Pilih supplier atau PO");
        BigDecimal total = BigDecimal.ZERO;
        for (SupplierReturn.Line l : d.getLines()) {
            require(l.getItemId() != null && l.getLocationId() != null && l.getQty() != null, "RTS_LINE",
                    "Baris " + l.getLineNo() + ": item, lokasi, qty wajib diisi");
            if (l.getPoLineId() == null && d.getPoId() != null) {
                l.setPoLineId(jdbc.queryForList("SELECT id FROM prc.po_line WHERE po_id = ? AND item_id = ? ORDER BY line_no LIMIT 1", Long.class,
                        d.getPoId(), l.getItemId()).stream().findFirst().orElse(null));
            }
            l.setUnitCost(inventory.unitCost(l.getItemId(), l.getLotId(), l.getLocationId()));
            l.setAmount(money(l.getQty().multiply(l.getUnitCost())));
            total = total.add(l.getAmount());
        }
        d.setTotal(total);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, SupplierReturn d) {
        body.put("partnerName", prc.partnerName(d.getPartnerId()));
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                m.put("lotNo", m.get("lotId") == null ? null : jdbc.queryForObject("SELECT lot_no || ' (' || qc_status || ')' FROM scm.lot WHERE id = ?",
                        String.class, ((Number) m.get("lotId")).longValue()));
            }
        }
    }
}

// =====================================================================================================================
// Master PRC-03/04/08
// =====================================================================================================================

@RestController
@RequestMapping("/api/prc/supplier-profiles")
class SupplierProfileController extends MasterController<SupplierProfile> {
    SupplierProfileController(SupplierProfileRepository r, PermissionService p) { super(r, p, SupplierProfile.class); }
    @Override protected String menuCode() { return "PRC-03"; }
    @Override protected List<String> searchFields() { return List.of("legalName", "nib", "halalCertNo"); }
    @Override protected Sort defaultSort() { return Sort.by("partnerId"); }
    @Override protected String[] immutableFields() { return new String[]{"partnerId"}; }
}

@RestController
@RequestMapping("/api/prc/asl")
class AslController extends MasterController<Asl> {
    AslController(AslRepository r, PermissionService p) { super(r, p, Asl.class); }
    @Override protected String menuCode() { return "PRC-04"; }
    @Override protected List<String> searchFields() { return List.of("manufacturer", "notes"); }
    @Override protected Sort defaultSort() { return Sort.by("itemId"); }
}

@RestController
@RequestMapping("/api/prc/price-lists")
class PriceListController extends MasterController<PriceList> {
    PriceListController(PriceListRepository r, PermissionService p) { super(r, p, PriceList.class); }
    @Override protected String menuCode() { return "PRC-08"; }
    @Override protected List<String> searchFields() { return List.of("contractNo"); }
    @Override protected Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "validFrom"); }

    @Override
    protected void beforeSave(PriceList e, PriceList existing) {
        if (e.getValidUntil() != null && e.getValidUntil().isBefore(e.getValidFrom())) {
            throw new BusinessException("PRICE_DATE", "Berlaku sampai tidak boleh sebelum berlaku mulai");
        }
    }
}

// =====================================================================================================================
// PRC-09 Monitoring kedatangan, PRC-13 Penilaian supplier, PRC-90 Laporan, dashboard & KPI
// =====================================================================================================================

@RestController
@RequestMapping("/api/prc")
class ProcurementReports {

    private final JdbcTemplate jdbc;
    private final PermissionService perm;

    ProcurementReports(JdbcTemplate jdbc, PermissionService perm) {
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @GetMapping("/arrivals")
    public List<Map<String, Object>> arrivals() {
        perm.require("PRC-09", Action.VIEW);
        return jdbc.queryForList("""
                SELECT l.id AS line_id, p.id AS po_id, p.doc_no, s.name AS supplier, i.code AS item_code, i.name AS item_name, u.code AS uom,
                       l.qty, l.received_qty, l.qty - l.received_qty AS outstanding, l.need_date, l.eta, l.followup_note, l.last_receipt,
                       COALESCE(l.eta, l.need_date) < current_date AS late
                FROM prc.po_line l JOIN prc.po p ON p.id = l.po_id JOIN sys.partner s ON s.id = p.partner_id
                JOIN sys.item i ON i.id = l.item_id JOIN sys.uom u ON u.id = i.uom_id
                WHERE p.plant_id = ? AND p.status = 'APPROVED' AND NOT l.closed AND l.qty > l.received_qty
                ORDER BY COALESCE(l.eta, l.need_date) NULLS LAST, p.doc_no""", UserContext.plantId());
    }

    /** Skor = 40% ketepatan waktu (GR vs tanggal kebutuhan) + 40% mutu (lot tidak di-reject QC) + 20% kepatuhan harga kontrak. */
    @GetMapping("/supplier-scores")
    public List<Map<String, Object>> scores(@RequestParam int year) {
        perm.require("PRC-13", Action.VIEW);
        Long plant = UserContext.plantId();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.id AS partner_id, s.code, s.name,
                       COUNT(DISTINCT l.id) AS lines,
                       COUNT(DISTINCT l.id) FILTER (WHERE l.received_qty >= l.qty AND l.last_receipt <= COALESCE(l.need_date, l.last_receipt)) AS on_time,
                       (SELECT COUNT(*) FROM scm.gr_line gl JOIN scm.gr g ON g.id = gl.gr_id JOIN scm.lot lt ON lt.id = gl.lot_id
                         WHERE g.partner_id = s.id AND g.status = 'POSTED' AND EXTRACT(YEAR FROM g.doc_date) = ?) AS lots,
                       (SELECT COUNT(*) FROM scm.gr_line gl JOIN scm.gr g ON g.id = gl.gr_id JOIN scm.lot lt ON lt.id = gl.lot_id
                         WHERE g.partner_id = s.id AND g.status = 'POSTED' AND EXTRACT(YEAR FROM g.doc_date) = ? AND lt.qc_status = 'REJECTED') AS rejected,
                       SUM(l.amount) AS spend,
                       SUM(l.amount) FILTER (WHERE pl.price IS NOT NULL AND l.unit_price <= pl.price) AS at_contract,
                       SUM(l.amount) FILTER (WHERE pl.price IS NOT NULL) AS with_contract
                FROM prc.po_line l JOIN prc.po p ON p.id = l.po_id JOIN sys.partner s ON s.id = p.partner_id
                LEFT JOIN LATERAL (SELECT price FROM prc.price_list x WHERE x.partner_id = p.partner_id AND x.item_id = l.item_id AND x.active
                                   AND x.valid_from <= p.doc_date AND (x.valid_until IS NULL OR x.valid_until >= p.doc_date) ORDER BY x.valid_from DESC LIMIT 1) pl ON TRUE
                WHERE p.plant_id = ? AND p.status IN ('APPROVED','DONE') AND EXTRACT(YEAR FROM p.doc_date) = ?
                GROUP BY s.id, s.code, s.name ORDER BY s.name""", year, year, plant, year);
        for (Map<String, Object> r : rows) {
            double lines = ((Number) r.get("lines")).doubleValue();
            double otd = lines == 0 ? 100 : ((Number) r.get("on_time")).doubleValue() * 100 / lines;
            double lots = ((Number) r.get("lots")).doubleValue();
            double quality = lots == 0 ? 100 : 100 - ((Number) r.get("rejected")).doubleValue() * 100 / lots;
            BigDecimal with = (BigDecimal) r.get("with_contract");
            double price = with == null || with.signum() == 0 ? 100
                    : (r.get("at_contract") == null ? 0 : ((BigDecimal) r.get("at_contract")).doubleValue()) * 100 / with.doubleValue();
            r.put("otd_pct", round(otd));
            r.put("quality_pct", round(quality));
            r.put("price_pct", round(price));
            double score = 0.4 * otd + 0.4 * quality + 0.2 * price;
            r.put("score", round(score));
            r.put("grade", score >= 90 ? "A" : score >= 75 ? "B" : score >= 60 ? "C" : "D");
        }
        return rows;
    }

    private static BigDecimal round(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
    }

    /** PRC-90: belanja per supplier & kategori, penghematan dari perbandingan penawaran, lead time PR → PO → GR. */
    @GetMapping("/report")
    public Map<String, Object> report(@RequestParam int year, @RequestParam int month) {
        perm.require("PRC-90", Action.VIEW);
        Long plant = UserContext.plantId();
        YearMonth ym = YearMonth.of(year, month);
        Date from = Date.valueOf(ym.atDay(1));
        Date to = Date.valueOf(ym.atEndOfMonth());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bySupplier", jdbc.queryForList("""
                SELECT s.name, COUNT(DISTINCT p.id) AS pos, SUM(p.total_idr) AS spend FROM prc.po p JOIN sys.partner s ON s.id = p.partner_id
                WHERE p.plant_id = ? AND p.status IN ('APPROVED','DONE') AND p.doc_date BETWEEN ? AND ? GROUP BY s.name ORDER BY spend DESC""", plant, from, to));
        out.put("byCategory", jdbc.queryForList("""
                SELECT COALESCE(i.category, i.type) AS category, SUM(l.amount * p.exchange_rate) AS spend
                FROM prc.po_line l JOIN prc.po p ON p.id = l.po_id JOIN sys.item i ON i.id = l.item_id
                WHERE p.plant_id = ? AND p.status IN ('APPROVED','DONE') AND p.doc_date BETWEEN ? AND ? GROUP BY 1 ORDER BY spend DESC""", plant, from, to));
        out.put("saving", jdbc.queryForObject("""
                SELECT COALESCE(SUM((mx.price - w.price) * rl.qty), 0) FROM prc.rfq r JOIN prc.rfq_line rl ON rl.rfq_id = r.id
                JOIN prc.rfq_quote w ON w.rfq_line_id = rl.id AND w.partner_id = r.awarded_partner_id
                JOIN LATERAL (SELECT MAX(price) AS price FROM prc.rfq_quote q WHERE q.rfq_line_id = rl.id) mx ON TRUE
                WHERE r.plant_id = ? AND r.status = 'DONE' AND r.doc_date BETWEEN ? AND ?""", BigDecimal.class, plant, from, to));
        out.put("leadTime", ProcurementKpis.leadTimes(jdbc, plant, ym));
        return out;
    }
}

@Component
class ProcurementKpis implements KpiProvider, DashboardProvider, LookupSource {

    private final JdbcTemplate jdbc;

    ProcurementKpis(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static Map<String, Object> leadTimes(JdbcTemplate jdbc, Long plant, YearMonth ym) {
        return jdbc.queryForMap("""
                SELECT AVG(EXTRACT(EPOCH FROM (p.approved_at - r.approved_at)) / 86400)::numeric(9,1) AS pr_to_po,
                       AVG(l.first_receipt - p.doc_date)::numeric(9,1) AS po_to_gr
                FROM prc.po_line l JOIN prc.po p ON p.id = l.po_id LEFT JOIN prc.pr_line pl ON pl.id = l.pr_line_id LEFT JOIN prc.pr r ON r.id = pl.pr_id
                WHERE p.plant_id = ? AND p.approved_at IS NOT NULL AND p.doc_date BETWEEN ? AND ?""",
                plant, Date.valueOf(ym.atDay(1)), Date.valueOf(ym.atEndOfMonth()));
    }

    @Override
    public Map<String, BigDecimal> compute(YearMonth period, Long plantId) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        Map<String, Object> lt = leadTimes(jdbc, plantId, period);
        if (lt.get("pr_to_po") != null) {
            out.put("PRC_LT", (BigDecimal) lt.get("pr_to_po"));
        }
        Map<String, Object> otd = jdbc.queryForMap("""
                SELECT COUNT(*) AS n, COUNT(*) FILTER (WHERE l.received_qty >= l.qty AND l.last_receipt <= l.need_date) AS ok
                FROM prc.po_line l JOIN prc.po p ON p.id = l.po_id
                WHERE p.plant_id = ? AND p.status IN ('APPROVED','DONE') AND l.need_date BETWEEN ? AND ? AND l.need_date < current_date""",
                plantId, Date.valueOf(period.atDay(1)), Date.valueOf(period.atEndOfMonth()));
        long n = ((Number) otd.get("n")).longValue();
        if (n > 0) {
            out.put("PRC_OTD", BigDecimal.valueOf(((Number) otd.get("ok")).longValue() * 100.0 / n).setScale(2, RoundingMode.HALF_UP));
        }
        return out;
    }

    @Override
    public String appCode() {
        return "PRC";
    }

    @Override
    public List<Kpi> kpis(Long plantId) {
        Long prOpen = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT r.id) FROM prc.pr r JOIN prc.pr_line l ON l.pr_id = r.id
                WHERE r.plant_id = ? AND r.status = 'APPROVED' AND NOT l.closed""", Long.class, plantId);
        Map<String, Object> po = jdbc.queryForMap("SELECT COUNT(*) AS n, COALESCE(SUM(total_idr), 0) AS v FROM prc.po WHERE plant_id = ? AND status = 'APPROVED'",
                plantId);
        Long late = jdbc.queryForObject("""
                SELECT COUNT(*) FROM prc.po_line l JOIN prc.po p ON p.id = l.po_id WHERE p.plant_id = ? AND p.status = 'APPROVED' AND NOT l.closed
                  AND l.qty > l.received_qty AND COALESCE(l.eta, l.need_date) < current_date""", Long.class, plantId);
        BigDecimal otd = compute(YearMonth.now(), plantId).get("PRC_OTD");
        return List.of(
                new Kpi("PR menunggu PO", String.valueOf(prOpen), "PR disetujui dengan baris terbuka", "PRC-02"),
                new Kpi("PO terbuka", String.valueOf(po.get("n")), "Rp " + String.format("%,.0f", (BigDecimal) po.get("v")).replace(',', '.'), "PRC-07"),
                new Kpi("Kedatangan terlambat", String.valueOf(late), "baris PO lewat ETA", "PRC-09"),
                new Kpi("Ketepatan supplier", otd == null ? "–" : otd.stripTrailingZeros().toPlainString() + "%", "bulan ini", "PRC-13"));
    }

    @Override
    public Map<String, Def> lookups() {
        return Map.of(
                "po-open", new Def("""
                        (SELECT p.id, p.doc_no, s.name, p.kind, p.partner_id, p.status, p.import_po::text AS import_po, p.plant_id
                         FROM prc.po p JOIN sys.partner s ON s.id = p.partner_id) x""",
                        "doc_no", "name", "kind", "status = 'APPROVED'", Set.of("partner_id", "kind", "import_po", "plant_id")),
                "po-received", new Def("""
                        (SELECT p.id, p.doc_no, s.name, p.kind, p.partner_id, p.status, p.plant_id FROM prc.po p JOIN sys.partner s ON s.id = p.partner_id) x""",
                        "doc_no", "name", "kind", "status IN ('APPROVED','DONE')", Set.of("partner_id", "kind", "plant_id")),
                "po-lines-open", new Def("""
                        (SELECT l.id, i.code, i.name || COALESCE(' · ' || l.description, '') AS name, (l.qty - l.received_qty)::text AS sisa,
                                l.qty - l.received_qty AS sisa_num, l.po_id, p.status, i.type AS item_type, l.closed
                         FROM prc.po_line l JOIN prc.po p ON p.id = l.po_id JOIN sys.item i ON i.id = l.item_id) x""",
                        "code", "name", "sisa", "status = 'APPROVED' AND NOT closed AND sisa_num > 0", Set.of("po_id", "item_type")),
                "po-lines", new Def("""
                        (SELECT l.id, i.code, i.name, ('diterima ' || l.received_qty)::text AS info, l.po_id FROM prc.po_line l
                         JOIN sys.item i ON i.id = l.item_id) x""", "code", "name", "info", null, Set.of("po_id")),
                "pr-lines-open", new Def("""
                        (SELECT l.id, r.doc_no, i.code || ' · ' || i.name AS name, (l.qty - l.qty_ordered)::text AS sisa, l.item_id, r.status, l.closed,
                                r.plant_id FROM prc.pr_line l JOIN prc.pr r ON r.id = l.pr_id JOIN sys.item i ON i.id = l.item_id) x""",
                        "doc_no", "name", "sisa", "status = 'APPROVED' AND NOT closed", Set.of("item_id", "plant_id")),
                "suppliers", new Def("""
                        (SELECT p.id, p.code, p.name, COALESCE(sp.qualification_status, 'BELUM') AS qual, p.active FROM sys.partner p
                         LEFT JOIN prc.supplier_profile sp ON sp.partner_id = p.id WHERE p.type IN ('SUPPLIER','BOTH','EXPEDITION')) x""",
                        "code", "name", "qual", "active", Set.of("qual")));
    }
}
