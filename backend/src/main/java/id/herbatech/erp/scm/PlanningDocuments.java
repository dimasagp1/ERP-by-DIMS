package id.herbatech.erp.scm;

import id.herbatech.erp.fin.FinApi;
import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.document.DocumentWorkflowService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

interface BomRepository extends DocumentRepository<Bom> {
}

interface SalesOrderRepository extends DocumentRepository<SalesOrder> {
}

// =====================================================================================================================
// SCM-05 Bill of Materials
// =====================================================================================================================

@Component
class BomHandler implements DocumentHandler<Bom> {

    private final BomRepository repo;
    private final InventoryService inventory;
    private final DocumentWorkflowService workflow;
    private final JdbcTemplate jdbc;

    BomHandler(BomRepository repo, InventoryService inventory, @Lazy DocumentWorkflowService workflow, JdbcTemplate jdbc) {
        this.repo = repo;
        this.inventory = inventory;
        this.workflow = workflow;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "BOM"; }
    @Override public String periodModule() { return "SCM"; }
    @Override public Bom load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("BOM", id)); }
    @Override public Bom save(Bom doc) { return repo.save(doc); }

    @Override
    public String summary(Bom d) {
        InventoryService.ItemInfo i = inventory.item(d.getItemId());
        return "%s · rev %d · per %s %s".formatted(i == null ? "?" : i.name(), d.getRevision(),
                d.getBaseQty() == null ? "?" : d.getBaseQty().stripTrailingZeros().toPlainString(), i == null ? "" : i.uom());
    }

    /** Disetujui RnD & QA = langsung berlaku. */
    @Override public boolean autoPost(Bom doc) { return true; }

    @Override
    public boolean allows(String action, Bom doc) {
        return !"REVERSE".equals(action);
    }

    @Override
    public void validateSubmit(Bom d) {
        InventoryService.ItemInfo product = inventory.requireItem(d.getItemId());
        if (!"FG".equals(product.type()) && !"WIP".equals(product.type())) {
            throw new BusinessException("BOM_ITEM", "BOM hanya untuk barang jadi atau WIP");
        }
        if (d.getBaseQty() == null || d.getBaseQty().signum() <= 0) {
            throw new BusinessException("BOM_BASE", "Jumlah dasar BOM harus lebih dari nol");
        }
        if (d.getLines().isEmpty()) {
            throw new BusinessException("BOM_LINES", "Isi minimal satu komponen");
        }
        Set<Long> seen = new HashSet<>();
        for (Bom.Line l : d.getLines()) {
            if (l.getComponentItemId() == null || l.getQty() == null || l.getQty().signum() <= 0) {
                throw new BusinessException("BOM_LINE", "Baris " + l.getLineNo() + ": komponen dan qty wajib diisi");
            }
            if (l.getComponentItemId().equals(d.getItemId())) {
                throw new BusinessException("BOM_SELF", "Produk tidak boleh menjadi komponennya sendiri");
            }
            if (!seen.add(l.getComponentItemId())) {
                throw new BusinessException("BOM_DUP", "Komponen " + inventory.item(l.getComponentItemId()).code() + " tercantum dua kali");
            }
            Long loop = jdbc.queryForObject("""
                    SELECT count(*) FROM scm.bom b JOIN scm.bom_line bl ON bl.bom_id = b.id
                    WHERE b.status = 'POSTED' AND b.item_id = ? AND bl.component_item_id = ?""", Long.class, l.getComponentItemId(), d.getItemId());
            if (loop != null && loop > 0) {
                throw new BusinessException("BOM_LOOP", "BOM melingkar: komponen " + inventory.item(l.getComponentItemId()).code()
                        + " memakai produk ini");
            }
        }
    }

    /** BOM berlaku sebelumnya untuk produk yang sama di plant ini menjadi Selesai (riwayat revisi tetap tersimpan). */
    @Override
    public void onPost(Bom d) {
        validateSubmit(d);
        for (Long old : jdbc.queryForList("SELECT id FROM scm.bom WHERE item_id = ? AND plant_id = ? AND status = 'POSTED' AND id <> ?",
                Long.class, d.getItemId(), d.getPlantId(), d.getId())) {
            workflow.markDone("BOM", old, "Digantikan " + d.getDocNo() + " (rev " + d.getRevision() + ")");
        }
        if (d.getEffectiveFrom() == null) {
            d.setEffectiveFrom(d.getDocDate());
        }
    }
}

@RestController
@RequestMapping("/api/scm/boms")
class BomController extends DocumentApi<Bom> {

    private final ScmSupport scm;
    private final BomQueries boms;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;

    BomController(BomHandler handler, BomRepository repo, Support support, ScmSupport scm, BomQueries boms, JdbcTemplate jdbc,
                  PermissionService perm) {
        super(handler, repo, support, Bom.class);
        this.scm = scm;
        this.boms = boms;
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "notes");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("revision");
    }

    @Override
    protected void apply(Bom target, Bom in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (Bom.Line l : in.getLines()) {
            l.setId(null);
            l.setBom(target);
            l.setLineNo(no++);
            if (l.getScrapPct() == null) {
                l.setScrapPct(BigDecimal.ZERO);
            }
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(Bom d, boolean isNew) {
        require(d.getItemId() != null, "BOM_ITEM", "Produk wajib dipilih");
        if (d.getStdHours() == null) {
            d.setStdHours(BigDecimal.ZERO);
        }
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(revision), 0) FROM scm.bom WHERE item_id = ? AND id <> ? AND status <> 'CANCELLED'",
                Integer.class, d.getItemId(), d.getId() == null ? -1L : d.getId());
        d.setRevision(max + 1);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, Bom d) {
        body.put("itemName", scm.itemLabel(d.getItemId()));
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                InventoryService.ItemInfo i = boms.inventory().item(((Number) m.get("componentItemId")).longValue());
                if (i != null) {
                    m.put("uom", i.uom());
                    m.put("itemType", i.type());
                }
            }
        }
    }

    /** BOM berlaku untuk satu produk (dipakai permintaan bahan PRE-04, MRP, standard cost). */
    @GetMapping("/active")
    public BomQueries.ActiveBom active(@RequestParam Long itemId) {
        perm.require("SCM-05", Action.VIEW);
        return boms.active(UserContext.plantId(), itemId).orElse(null);
    }

    @GetMapping("/explode")
    public List<BomQueries.Requirement> explode(@RequestParam Long itemId, @RequestParam BigDecimal qty) {
        perm.require("SCM-05", Action.VIEW);
        return boms.explode(UserContext.plantId(), itemId, qty);
    }
}

// =====================================================================================================================
// SCM-02 Pesanan pelanggan
// =====================================================================================================================

@Component
class SalesOrderHandler implements DocumentHandler<SalesOrder> {

    private final SalesOrderRepository repo;
    private final ScmSupport scm;
    private final FinApi fin;
    private final InventoryService inventory;
    private final JdbcTemplate jdbc;

    SalesOrderHandler(SalesOrderRepository repo, ScmSupport scm, FinApi fin, InventoryService inventory, JdbcTemplate jdbc) {
        this.repo = repo;
        this.scm = scm;
        this.fin = fin;
        this.inventory = inventory;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "SO"; }
    @Override public String periodModule() { return "SCM"; }
    @Override public SalesOrder load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Pesanan", id)); }
    @Override public SalesOrder save(SalesOrder doc) { return repo.save(doc); }

    @Override
    public String summary(SalesOrder d) {
        return scm.partnerName(d.getPartnerId()) + (d.getCustomerPo() == null ? "" : " · PO " + d.getCustomerPo())
                + (d.isCreditHold() ? " · KREDIT TERTAHAN" : "");
    }

    @Override public BigDecimal amount(SalesOrder d) { return d.getTotal(); }

    /** Pesanan dikonfirmasi saat disetujui; selesai saat terkirim penuh lewat surat jalan. */
    @Override
    public boolean allows(String action, SalesOrder doc) {
        return !"POST".equals(action) && !"REVERSE".equals(action);
    }

    @Override
    public Set<String> approvalFlags(SalesOrder d) {
        return d.isCreditHold() ? Set.of("CREDIT_HOLD") : Set.of();
    }

    @Override
    public void validateSubmit(SalesOrder d) {
        scm.requirePartner(d.getPartnerId(), "CUSTOMER");
        if (d.getLines().isEmpty()) {
            throw new BusinessException("SO_LINES", "Isi minimal satu baris produk");
        }
        for (SalesOrder.Line l : d.getLines()) {
            InventoryService.ItemInfo i = inventory.requireItem(l.getItemId());
            if (!"FG".equals(i.type())) {
                throw new BusinessException("SO_ITEM", "Baris " + l.getLineNo() + ": hanya barang jadi yang bisa dipesan");
            }
            if (l.getQty() == null || l.getQty().signum() <= 0 || l.getUnitPrice().signum() <= 0) {
                throw new BusinessException("SO_LINE", "Baris " + l.getLineNo() + ": qty dan harga harus lebih dari nol");
            }
        }
        if (d.getDeliveryDate() == null || d.getDeliveryDate().isBefore(d.getDocDate())) {
            throw new BusinessException("SO_DATE", "Tanggal kirim tidak boleh sebelum tanggal pesanan");
        }
        BigDecimal pending = jdbc.queryForObject("""
                SELECT COALESCE(SUM((l.qty - l.qty_shipped) * l.unit_price * (1 - l.discount_pct / 100)), 0)
                FROM scm.so_line l JOIN scm.so s ON s.id = l.so_id
                WHERE s.partner_id = ? AND s.id <> ? AND s.status = 'APPROVED' AND NOT l.closed""",
                BigDecimal.class, d.getPartnerId(), d.getId());
        FinApi.CreditStatus c = fin.credit(d.getPartnerId(), d.getTotal(), pending, d.getDocDate());
        d.setCreditHold(c.hold());
        d.setHoldReason(c.reason());
    }
}

@RestController
@RequestMapping("/api/scm/sales-orders")
class SalesOrderController extends DocumentApi<SalesOrder> {

    private final SalesOrderHandler so;
    private final ScmSupport scm;
    private final FinApi fin;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;

    SalesOrderController(SalesOrderHandler handler, SalesOrderRepository repo, Support support, ScmSupport scm, FinApi fin,
                         JdbcTemplate jdbc, PermissionService perm) {
        super(handler, repo, support, SalesOrder.class);
        this.so = handler;
        this.scm = scm;
        this.fin = fin;
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "customerPo", "notes");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("subtotal", "ppnAmount", "total", "creditHold", "holdReason");
    }

    @Override
    protected void apply(SalesOrder target, SalesOrder in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (SalesOrder.Line l : in.getLines()) {
            l.setId(null);
            l.setOrder(target);
            l.setLineNo(no++);
            l.setQtyShipped(BigDecimal.ZERO);
            l.setClosed(false);
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(SalesOrder d, boolean isNew) {
        require(d.getPartnerId() != null, "SO_PARTNER", "Customer wajib dipilih");
        if (d.getDeliveryDate() == null) {
            d.setDeliveryDate(d.getDocDate().plusDays(7));
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (SalesOrder.Line l : d.getLines()) {
            if (l.getUnitPrice() == null) l.setUnitPrice(BigDecimal.ZERO);
            if (l.getDiscountPct() == null) l.setDiscountPct(BigDecimal.ZERO);
            if (l.getDeliveryDate() == null) l.setDeliveryDate(d.getDeliveryDate());
            l.setAmount(ScmSupport.net(l.getQty(), l.getUnitPrice(), l.getDiscountPct()));
            subtotal = subtotal.add(l.getAmount());
        }
        d.setSubtotal(subtotal);
        d.setPpnAmount(d.isWithPpn() ? fin.ppn(subtotal) : BigDecimal.ZERO);
        d.setTotal(subtotal.add(d.getPpnAmount()));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, SalesOrder d) {
        body.put("partnerName", scm.partnerName(d.getPartnerId()));
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                Long item = ((Number) m.get("itemId")).longValue();
                m.put("available", jdbc.queryForObject("""
                        SELECT COALESCE(SUM(q.qty - q.qty_reserved), 0) FROM scm.stock_quant q JOIN scm.lot l ON l.id = q.lot_id
                        JOIN sys.location loc ON loc.id = q.location_id JOIN sys.warehouse w ON w.id = loc.warehouse_id
                        WHERE q.item_id = ? AND w.plant_id = ? AND l.qc_status = 'RELEASED' AND NOT loc.is_quarantine
                          AND (l.exp_date IS NULL OR l.exp_date >= current_date)""", BigDecimal.class, item, d.getPlantId()));
                BigDecimal qty = new BigDecimal(m.get("qty").toString());
                BigDecimal shipped = new BigDecimal(m.get("qtyShipped").toString());
                m.put("remaining", qty.subtract(shipped));
            }
        }
    }

    record OpenLine(Long soLineId, Long itemId, String itemLabel, BigDecimal qty, BigDecimal shipped, BigDecimal remaining,
                    BigDecimal unitPrice, BigDecimal discountPct) {
    }

    /** Baris pesanan yang belum terkirim (bahan surat jalan SCM-26). */
    @GetMapping("/{id}/open-lines")
    public List<OpenLine> openLines(@PathVariable Long id) {
        perm.require("SCM-26", Action.VIEW);
        return jdbc.query("""
                SELECT l.id, l.item_id, i.code || ' · ' || i.name, l.qty, l.qty_shipped, l.qty - l.qty_shipped, l.unit_price, l.discount_pct
                FROM scm.so_line l JOIN sys.item i ON i.id = l.item_id JOIN scm.so s ON s.id = l.so_id
                WHERE l.so_id = ? AND s.status = 'APPROVED' AND NOT l.closed AND l.qty > l.qty_shipped ORDER BY l.line_no""",
                (rs, i) -> new OpenLine(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5),
                        rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8)), id);
    }

    record CloseRequest(String reason) {
    }

    /** Tutup sisa pesanan yang tidak akan dikirim (pesanan menjadi Selesai). */
    @PostMapping("/{id}/close")
    @Transactional
    public Envelope close(@PathVariable Long id, @RequestBody CloseRequest req) {
        perm.require("SCM-02", Action.CANCEL);
        require(req.reason() != null && !req.reason().isBlank(), "REASON_REQUIRED", "Alasan penutupan wajib diisi");
        SalesOrder d = so.load(id);
        require(d.getStatus() == DocStatus.APPROVED, "SO_STATE", "Hanya pesanan terkonfirmasi yang bisa ditutup");
        d.getLines().forEach(l -> l.setClosed(true));
        so.save(d);
        workflow().markDone("SO", id, "Sisa pesanan ditutup: " + req.reason());
        return envelope(so.load(id));
    }
}
