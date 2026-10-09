package id.herbatech.erp.prc;

import id.herbatech.erp.fin.FinApi;
import id.herbatech.erp.scm.GoodsReceived;
import id.herbatech.erp.scm.MrpService;
import id.herbatech.erp.shared.config.TimeService;
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
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static id.herbatech.erp.prc.PrcSupport.money;
import static id.herbatech.erp.prc.PrcSupport.nz;

interface PurchaseRequisitionRepository extends DocumentRepository<PurchaseRequisition> {
}

interface RfqRepository extends DocumentRepository<Rfq> {
}

interface PurchaseOrderRepository extends DocumentRepository<PurchaseOrder> {
}

// =====================================================================================================================
// PRC-02 Purchase Requisition (juga ESS-04)
// =====================================================================================================================

@Component
class PurchaseRequisitionHandler implements DocumentHandler<PurchaseRequisition> {

    private final PurchaseRequisitionRepository repo;
    private final PrcSupport prc;
    private final FinApi fin;

    PurchaseRequisitionHandler(PurchaseRequisitionRepository repo, PrcSupport prc, FinApi fin) {
        this.repo = repo;
        this.prc = prc;
        this.fin = fin;
    }

    @Override public String docType() { return "PR"; }
    @Override public String periodModule() { return "SCM"; }
    @Override public PurchaseRequisition load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("PR", id)); }
    @Override public PurchaseRequisition save(PurchaseRequisition doc) { return repo.save(doc); }
    @Override public BigDecimal amount(PurchaseRequisition d) { return d.getTotalEst(); }

    /** PR tidak diposting; selesai otomatis saat semua baris menjadi PO. */
    @Override
    public boolean allows(String action, PurchaseRequisition doc) {
        return !"POST".equals(action) && !"REVERSE".equals(action);
    }

    @Override
    public String summary(PurchaseRequisition d) {
        String first = d.getLines().isEmpty() ? "" : prc.itemLabel(d.getLines().getFirst().getItemId());
        return "%s%s · %d baris%s".formatted("MRP".equals(d.getSource()) ? "[MRP] " : "", first, d.getLines().size(),
                d.getPurpose() == null ? "" : " · " + d.getPurpose());
    }

    @Override
    public Set<String> approvalFlags(PurchaseRequisition d) {
        return fin.overBudget(d.getPlantId(), d.getDocDate(), costs(d)) ? Set.of("OVER_BUDGET") : Set.of();
    }

    List<FinApi.CostLine> costs(PurchaseRequisition d) {
        return d.getLines().stream().filter(l -> l.getAccountId() != null)
                .map(l -> new FinApi.CostLine(l.getCostCenterId(), l.getAccountId(), l.getAmount())).toList();
    }

    @Override
    public void validateSubmit(PurchaseRequisition d) {
        if (d.getLines().isEmpty()) {
            throw new BusinessException("PR_LINES", "Isi minimal satu baris kebutuhan");
        }
        for (PurchaseRequisition.Line l : d.getLines()) {
            Map<String, Object> i = prc.item(l.getItemId());
            if (!"ACTIVE".equals(i.get("status"))) {
                throw new BusinessException("PR_ITEM", "Baris " + l.getLineNo() + ": item " + i.get("code") + " berstatus " + i.get("status"));
            }
            if (l.getQty() == null || l.getQty().signum() <= 0) {
                throw new BusinessException("PR_QTY", "Baris " + l.getLineNo() + ": qty harus lebih dari nol");
            }
            if (l.getNeedDate() == null || l.getNeedDate().isBefore(d.getDocDate())) {
                throw new BusinessException("PR_DATE", "Baris " + l.getLineNo() + ": tanggal kebutuhan tidak boleh sebelum tanggal PR");
            }
            if ("SVC".equals(i.get("type")) && l.getAccountId() == null) {
                throw new BusinessException("PR_ACCOUNT", "Baris " + l.getLineNo() + ": jasa wajib diberi akun beban & cost center");
            }
            if (l.getAccountId() != null) {
                fin.validateAccount(l.getAccountId(), l.getCostCenterId(), "Baris " + l.getLineNo());
            }
        }
    }
}

@RestController
@RequestMapping("/api/prc/requisitions")
class PurchaseRequisitionController extends DocumentApi<PurchaseRequisition> {

    private final PurchaseRequisitionHandler handler;
    private final PrcSupport prc;
    private final MrpService mrp;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;
    private final TimeService time;

    PurchaseRequisitionController(PurchaseRequisitionHandler handler, PurchaseRequisitionRepository repo, Support support, PrcSupport prc,
                                  MrpService mrp, JdbcTemplate jdbc, PermissionService perm, TimeService time) {
        super(handler, repo, support, PurchaseRequisition.class);
        this.time = time;
        this.handler = handler;
        this.prc = prc;
        this.mrp = mrp;
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "purpose");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("totalEst", "source");
    }

    @Override
    protected void apply(PurchaseRequisition target, PurchaseRequisition in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (PurchaseRequisition.Line l : in.getLines()) {
            l.setId(null);
            l.setRequisition(target);
            l.setLineNo(no++);
            l.setQtyOrdered(BigDecimal.ZERO);
            l.setClosed(false);
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(PurchaseRequisition d, boolean isNew) {
        if (d.getNeedDate() == null) {
            d.setNeedDate(d.getDocDate().plusDays(14));
        }
        if (d.getCostCenterId() == null) {
            d.setCostCenterId(jdbc.queryForList("""
                    SELECT e.cost_center_id FROM sys.app_user u JOIN hc.employee e ON e.id = u.employee_id WHERE u.id = ?""",
                    Long.class, UserContext.userId()).stream().findFirst().orElse(null));
        }
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseRequisition.Line l : d.getLines()) {
            if (l.getNeedDate() == null) l.setNeedDate(d.getNeedDate());
            if (l.getCostCenterId() == null) l.setCostCenterId(d.getCostCenterId());
            if (l.getEstPrice() == null || l.getEstPrice().signum() == 0) {
                l.setEstPrice(l.getItemId() == null ? BigDecimal.ZERO : prc.bestPrice(l.getItemId(), d.getDocDate()));
            }
            l.setAmount(money(nz(l.getQty()).multiply(l.getEstPrice())));
            total = total.add(l.getAmount());
        }
        d.setTotalEst(total);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, PurchaseRequisition d) {
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                Map<String, Object> i = prc.item(((Number) m.get("itemId")).longValue());
                m.put("itemLabel", i.get("code") + " · " + i.get("name"));
                m.put("uom", i.get("uom"));
            }
        }
    }

    record FromMrp(List<Long> lineIds, String purpose) {
    }

    /** PR otomatis dari usulan beli MRP (SCM-05). */
    @PostMapping("/from-mrp")
    @Transactional
    public Envelope fromMrp(@RequestBody FromMrp req) {
        perm.require("PRC-02", Action.CREATE);
        List<MrpService.Suggestion> sugg = mrp.suggestions(req.lineIds() == null ? List.of() : req.lineIds());
        require(!sugg.isEmpty(), "PR_MRP", "Pilih baris usulan beli MRP yang belum menjadi PR");
        PurchaseRequisition pr = new PurchaseRequisition();
        pr.setDocDate(time.today());
        pr.setSource("MRP");
        pr.setPurpose(req.purpose() == null || req.purpose().isBlank() ? "Usulan MRP" : req.purpose());
        short no = 1;
        for (MrpService.Suggestion sg : sugg) {
            PurchaseRequisition.Line l = new PurchaseRequisition.Line();
            l.setRequisition(pr);
            l.setLineNo(no++);
            l.setItemId(sg.itemId());
            l.setQty(sg.plannedQty());
            l.setNeedDate(sg.needDate().isBefore(pr.getDocDate()) ? pr.getDocDate() : sg.needDate());
            l.setEstPrice(sg.estPrice());
            l.setSuggestedPartnerId(sg.partnerId());
            pr.getLines().add(l);
        }
        beforeSave(pr, true);
        PurchaseRequisition saved = workflow().initDraft(handler, pr);
        for (int k = 0; k < sugg.size(); k++) {
            mrp.markRequested(sugg.get(k).lineId(), saved.getLines().get(k).getId());
        }
        return envelope(saved);
    }

    /** Baris PR disetujui yang belum menjadi PO (bahan RFQ & PO). */
    @GetMapping("/open-lines")
    public List<Map<String, Object>> openLines() {
        perm.require("PRC-07", Action.VIEW);
        return jdbc.queryForList("""
                SELECT l.id, r.id AS pr_id, r.doc_no AS pr_no, l.item_id, i.code || ' · ' || i.name AS item_label, u.code AS uom,
                       l.qty - l.qty_ordered AS remaining, l.need_date, l.est_price, l.cost_center_id, l.account_id,
                       l.suggested_partner_id, p.name AS suggested_partner
                FROM prc.pr_line l JOIN prc.pr r ON r.id = l.pr_id JOIN sys.item i ON i.id = l.item_id JOIN sys.uom u ON u.id = i.uom_id
                LEFT JOIN sys.partner p ON p.id = l.suggested_partner_id
                WHERE r.plant_id = ? AND r.status = 'APPROVED' AND NOT l.closed AND l.qty > l.qty_ordered ORDER BY l.need_date, r.doc_no""",
                UserContext.plantId());
    }
}

// =====================================================================================================================
// PRC-05/06 RFQ & perbandingan penawaran
// =====================================================================================================================

@Component
class RfqHandler implements DocumentHandler<Rfq> {

    private final RfqRepository repo;
    private final PrcSupport prc;

    RfqHandler(RfqRepository repo, PrcSupport prc) {
        this.repo = repo;
        this.prc = prc;
    }

    @Override public String docType() { return "RFQ"; }
    @Override public String periodModule() { return "SCM"; }
    @Override public Rfq load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("RFQ", id)); }
    @Override public Rfq save(Rfq doc) { return repo.save(doc); }

    /** Diajukan = dikirim ke supplier; selesai saat pemenang dipilih dan PO terbentuk. */
    @Override
    public boolean allows(String action, Rfq doc) {
        return !"POST".equals(action) && !"REVERSE".equals(action);
    }

    @Override
    public String summary(Rfq d) {
        String first = d.getLines().isEmpty() ? "" : prc.itemLabel(d.getLines().getFirst().getItemId());
        return "%s · %d baris · %d supplier".formatted(first, d.getLines().size(), d.getSuppliers().size());
    }

    @Override
    public void validateSubmit(Rfq d) {
        if (d.getLines().isEmpty()) {
            throw new BusinessException("RFQ_LINES", "Isi minimal satu item");
        }
        if (d.getSuppliers().isEmpty()) {
            throw new BusinessException("RFQ_SUPPLIERS", "Pilih supplier yang diundang");
        }
        for (Rfq.Supplier s : d.getSuppliers()) {
            prc.requireQualifiedSupplier(s.getPartnerId());
        }
        if (d.getDueDate() == null || d.getDueDate().isBefore(d.getDocDate())) {
            throw new BusinessException("RFQ_DUE", "Batas waktu penawaran tidak boleh sebelum tanggal RFQ");
        }
    }
}

@RestController
@RequestMapping("/api/prc/rfqs")
class RfqController extends DocumentApi<Rfq> {

    private final RfqHandler handler;
    private final PurchaseOrderHandler poHandler;
    private final PrcSupport prc;
    private final FinApi fin;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;
    private final TimeService time;

    RfqController(RfqHandler handler, RfqRepository repo, Support support, PurchaseOrderHandler poHandler, PrcSupport prc, FinApi fin,
                  JdbcTemplate jdbc, PermissionService perm, TimeService time) {
        super(handler, repo, support, Rfq.class);
        this.time = time;
        this.handler = handler;
        this.poHandler = poHandler;
        this.prc = prc;
        this.fin = fin;
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "notes");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("awardedPartnerId", "awardReason", "poId");
    }

    @Override
    protected void apply(Rfq target, Rfq in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (Rfq.Line l : in.getLines()) {
            l.setId(null);
            l.setRfq(target);
            l.setLineNo(no++);
            target.getLines().add(l);
        }
        target.getSuppliers().clear();
        no = 1;
        for (Rfq.Supplier s : in.getSuppliers()) {
            s.setId(null);
            s.setRfq(target);
            s.setLineNo(no++);
            target.getSuppliers().add(s);
        }
    }

    @Override
    protected void beforeSave(Rfq d, boolean isNew) {
        if (d.getDueDate() == null) {
            d.setDueDate(d.getDocDate().plusDays(7));
        }
        for (Rfq.Line l : d.getLines()) {
            require(l.getItemId() != null && l.getQty() != null && l.getQty().signum() > 0, "RFQ_LINE", "Setiap baris wajib berisi item & qty");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, Rfq d) {
        body.put("awardedPartnerName", prc.partnerName(d.getAwardedPartnerId()));
        if (d.getPoId() != null) {
            body.put("poNo", jdbc.queryForObject("SELECT doc_no FROM prc.po WHERE id = ?", String.class, d.getPoId()));
        }
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                m.put("itemLabel", prc.itemLabel(((Number) m.get("itemId")).longValue()));
            }
        }
    }

    record Quote(Long rfqLineId, Long partnerId, BigDecimal price, Integer leadTimeDays, Integer paymentTermDays, LocalDate validUntil,
                 String notes) {
    }

    /** Matriks perbandingan: baris × supplier, total per supplier, termurah per baris (PRC-06). */
    @GetMapping("/{id}/quotes")
    public Map<String, Object> quotes(@PathVariable Long id) {
        perm.require("PRC-06", Action.VIEW);
        Rfq d = handler.load(id);
        List<Map<String, Object>> quotes = jdbc.queryForList("""
                SELECT q.*, p.name AS partner_name FROM prc.rfq_quote q JOIN sys.partner p ON p.id = q.partner_id WHERE q.rfq_id = ?""", id);
        List<Map<String, Object>> suppliers = new ArrayList<>();
        for (Rfq.Supplier s : d.getSuppliers()) {
            BigDecimal total = BigDecimal.ZERO;
            int quoted = 0;
            Integer lead = null;
            for (Rfq.Line l : d.getLines()) {
                for (Map<String, Object> q : quotes) {
                    if (((Number) q.get("rfq_line_id")).longValue() == l.getId() && ((Number) q.get("partner_id")).longValue() == s.getPartnerId()) {
                        total = total.add(money(l.getQty().multiply((BigDecimal) q.get("price"))));
                        quoted++;
                        if (q.get("lead_time_days") != null) {
                            lead = Math.max(lead == null ? 0 : lead, ((Number) q.get("lead_time_days")).intValue());
                        }
                    }
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("partnerId", s.getPartnerId());
            m.put("partnerName", prc.partnerName(s.getPartnerId()));
            m.put("total", total);
            m.put("complete", quoted == d.getLines().size());
            m.put("leadTimeDays", lead);
            suppliers.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("quotes", quotes);
        out.put("suppliers", suppliers);
        out.put("minQuotes", fin.paramNum("RFQ_MIN_QUOTES", "3"));
        out.put("threshold", fin.paramNum("RFQ_THRESHOLD", "50000000"));
        return out;
    }

    @PutMapping("/{id}/quotes")
    @Transactional
    public Map<String, Object> saveQuotes(@PathVariable Long id, @RequestBody List<Quote> quotes) {
        perm.require("PRC-06", Action.EDIT);
        Rfq d = handler.load(id);
        require(d.getStatus() == DocStatus.APPROVED, "RFQ_STATE", "Penawaran diisi setelah RFQ dikirim dan sebelum pemenang dipilih");
        for (Quote q : quotes) {
            require(d.getLines().stream().anyMatch(l -> l.getId().equals(q.rfqLineId()))
                    && d.getSuppliers().stream().anyMatch(s -> s.getPartnerId().equals(q.partnerId())), "RFQ_QUOTE", "Penawaran tidak cocok dengan RFQ");
            if (q.price() == null) {
                jdbc.update("DELETE FROM prc.rfq_quote WHERE rfq_line_id = ? AND partner_id = ?", q.rfqLineId(), q.partnerId());
                continue;
            }
            jdbc.update("""
                    INSERT INTO prc.rfq_quote (rfq_id, rfq_line_id, partner_id, price, lead_time_days, payment_term_days, valid_until, notes, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (rfq_line_id, partner_id) DO UPDATE SET price = EXCLUDED.price, lead_time_days = EXCLUDED.lead_time_days,
                        payment_term_days = EXCLUDED.payment_term_days, valid_until = EXCLUDED.valid_until, notes = EXCLUDED.notes""",
                    id, q.rfqLineId(), q.partnerId(), q.price(), q.leadTimeDays(), q.paymentTermDays(),
                    q.validUntil() == null ? null : Date.valueOf(q.validUntil()), q.notes(), UserContext.userId());
        }
        return Map.of("saved", quotes.size());
    }

    record Award(Long partnerId, String reason) {
    }

    /** Pilih pemenang → draft PO dari penawaran pemenang; di atas batas nilai wajib ≥ N penawaran lengkap atau alasan. */
    @PostMapping("/{id}/award")
    @Transactional
    public Map<String, Object> award(@PathVariable Long id, @RequestBody Award req) {
        perm.require("PRC-06", Action.CREATE);
        Rfq d = handler.load(id);
        require(d.getStatus() == DocStatus.APPROVED, "RFQ_STATE", "RFQ belum dikirim atau sudah selesai");
        Map<String, Object> cmp = quotes(id);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sup = (List<Map<String, Object>>) cmp.get("suppliers");
        Map<String, Object> win = sup.stream().filter(s -> s.get("partnerId").equals(req.partnerId())).findFirst()
                .orElseThrow(() -> new BusinessException("RFQ_AWARD", "Supplier tidak diundang di RFQ ini"));
        require((Boolean) win.get("complete"), "RFQ_AWARD", "Pemenang harus memberi penawaran untuk semua item");
        long complete = sup.stream().filter(s -> (Boolean) s.get("complete")).count();
        BigDecimal threshold = (BigDecimal) cmp.get("threshold");
        int min = ((BigDecimal) cmp.get("minQuotes")).intValue();
        boolean cheapest = sup.stream().filter(s -> (Boolean) s.get("complete"))
                .allMatch(s -> ((BigDecimal) win.get("total")).compareTo((BigDecimal) s.get("total")) <= 0);
        if (((BigDecimal) win.get("total")).compareTo(threshold) > 0 && complete < min && (req.reason() == null || req.reason().isBlank())) {
            throw BusinessException.of("RFQ_MIN", "Nilai di atas batas: butuh minimal %d penawaran lengkap (baru %d) atau alasan", min, complete);
        }
        if (!cheapest && (req.reason() == null || req.reason().isBlank())) {
            throw new BusinessException("RFQ_REASON", "Pemenang bukan penawar termurah: wajib isi alasan pemilihan");
        }
        PurchaseOrder po = new PurchaseOrder();
        po.setDocDate(time.today());
        po.setPartnerId(req.partnerId());
        po.setRfqId(id);
        po.setNotes("Dari " + d.getDocNo());
        if (complete < min) {
            po.setSingleSourceReason(req.reason());
        }
        short no = 1;
        for (Rfq.Line l : d.getLines()) {
            Map<String, Object> q = jdbc.queryForMap("SELECT price, payment_term_days FROM prc.rfq_quote WHERE rfq_line_id = ? AND partner_id = ?",
                    l.getId(), req.partnerId());
            if (q.get("payment_term_days") != null) {
                po.setPaymentTermDays(((Number) q.get("payment_term_days")).intValue());
            }
            PurchaseOrder.Line pl = new PurchaseOrder.Line();
            pl.setOrder(po);
            pl.setLineNo(no++);
            pl.setItemId(l.getItemId());
            pl.setDescription(l.getDescription());
            pl.setQty(l.getQty());
            pl.setUnitPrice((BigDecimal) q.get("price"));
            pl.setPrLineId(l.getPrLineId());
            if (l.getPrLineId() != null) {
                jdbc.query("SELECT need_date, cost_center_id, account_id FROM prc.pr_line WHERE id = ?", rs -> {
                    pl.setNeedDate(rs.getDate(1) == null ? null : rs.getDate(1).toLocalDate());
                    pl.setCostCenterId((Long) rs.getObject(2));
                    pl.setAccountId((Long) rs.getObject(3));
                }, l.getPrLineId());
            }
            po.getLines().add(pl);
        }
        poHandler.recalc(po);
        PurchaseOrder saved = workflow().initDraft(poHandler, po);
        d.setAwardedPartnerId(req.partnerId());
        d.setAwardReason(req.reason());
        d.setPoId(saved.getId());
        handler.save(d);
        workflow().markDone("RFQ", id, "Pemenang " + prc.partnerName(req.partnerId()) + " → " + saved.getDocNo());
        return Map.of("poId", saved.getId(), "poNo", saved.getDocNo());
    }
}

// =====================================================================================================================
// PRC-07 Purchase Order
// =====================================================================================================================

@Component
class PurchaseOrderHandler implements DocumentHandler<PurchaseOrder> {

    private final PurchaseOrderRepository repo;
    private final PurchaseRequisitionRepository prs;
    private final PrcSupport prc;
    private final FinApi fin;
    private final DocumentWorkflowService workflow;
    private final JdbcTemplate jdbc;

    PurchaseOrderHandler(PurchaseOrderRepository repo, PurchaseRequisitionRepository prs, PrcSupport prc, FinApi fin,
                         @Lazy DocumentWorkflowService workflow, JdbcTemplate jdbc) {
        this.repo = repo;
        this.prs = prs;
        this.prc = prc;
        this.fin = fin;
        this.workflow = workflow;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "PO"; }
    @Override public String periodModule() { return "SCM"; }
    @Override public PurchaseOrder load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("PO", id)); }
    @Override public PurchaseOrder save(PurchaseOrder doc) { return repo.save(doc); }
    @Override public BigDecimal amount(PurchaseOrder d) { return d.getTotalIdr(); }

    /** PO tidak diposting: disetujui = terbit ke supplier; selesai saat diterima penuh atau ditutup. */
    @Override
    public boolean allows(String action, PurchaseOrder doc) {
        return !"POST".equals(action) && !"REVERSE".equals(action);
    }

    @Override
    public String summary(PurchaseOrder d) {
        return "%s · %d baris%s".formatted(prc.partnerName(d.getPartnerId()), d.getLines().size(),
                "SERVICE".equals(d.getKind()) ? " · jasa" : d.isImportPo() ? " · impor" : "");
    }

    List<FinApi.CostLine> costs(PurchaseOrder d) {
        return d.getLines().stream().filter(l -> l.getAccountId() != null)
                .map(l -> new FinApi.CostLine(l.getCostCenterId(), l.getAccountId(), money(l.getAmount().multiply(d.getExchangeRate())))).toList();
    }

    boolean singleSource(PurchaseOrder d) {
        BigDecimal threshold = fin.paramNum("RFQ_THRESHOLD", "50000000");
        if (d.getTotalIdr().compareTo(threshold) <= 0) {
            return false;
        }
        if (d.getRfqId() == null) {
            return true;
        }
        Long quoted = jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT partner_id FROM prc.rfq_quote WHERE rfq_id = ? GROUP BY partner_id
                HAVING count(*) = (SELECT count(*) FROM prc.rfq_line WHERE rfq_id = ?)) x""", Long.class, d.getRfqId(), d.getRfqId());
        return quoted == null || quoted < fin.paramNum("RFQ_MIN_QUOTES", "3").longValue();
    }

    @Override
    public Set<String> approvalFlags(PurchaseOrder d) {
        java.util.HashSet<String> flags = new java.util.HashSet<>();
        if (fin.overBudget(d.getPlantId(), d.getDocDate(), costs(d))) {
            flags.add("OVER_BUDGET");
        }
        if (singleSource(d)) {
            flags.add("SINGLE_SOURCE");
        }
        return flags;
    }

    @Override
    public void validateSubmit(PurchaseOrder d) {
        prc.requireQualifiedSupplier(d.getPartnerId());
        if (d.getLines().isEmpty()) {
            throw new BusinessException("PO_LINES", "Isi minimal satu baris");
        }
        Map<Long, BigDecimal> perPr = new LinkedHashMap<>();
        for (PurchaseOrder.Line l : d.getLines()) {
            Map<String, Object> i = prc.item(l.getItemId());
            if (!"ACTIVE".equals(i.get("status"))) {
                throw new BusinessException("PO_ITEM", "Baris " + l.getLineNo() + ": item " + i.get("code") + " berstatus " + i.get("status"));
            }
            if (l.getQty() == null || l.getQty().signum() <= 0 || l.getUnitPrice() == null || l.getUnitPrice().signum() <= 0) {
                throw new BusinessException("PO_LINE", "Baris " + l.getLineNo() + ": qty dan harga harus lebih dari nol");
            }
            prc.requireAsl(l.getItemId(), d.getPartnerId(), d.getDocDate(), "Baris " + l.getLineNo());
            if ("SVC".equals(i.get("type"))) {
                if (l.getAccountId() == null) {
                    throw new BusinessException("PO_ACCOUNT", "Baris " + l.getLineNo() + ": jasa wajib diberi akun beban & cost center");
                }
                fin.validateAccount(l.getAccountId(), l.getCostCenterId(), "Baris " + l.getLineNo());
            }
            if (l.getPrLineId() != null) {
                perPr.merge(l.getPrLineId(), l.getQty(), BigDecimal::add);
            }
        }
        for (Map.Entry<Long, BigDecimal> e : perPr.entrySet()) {
            Map<String, Object> pl = jdbc.queryForList("""
                    SELECT l.qty - l.qty_ordered AS remaining, r.status, r.doc_no FROM prc.pr_line l JOIN prc.pr r ON r.id = l.pr_id WHERE l.id = ?""",
                    e.getKey()).stream().findFirst().orElseThrow(() -> new BusinessException("PO_PR", "Baris PR tidak ditemukan"));
            if (!"APPROVED".equals(pl.get("status"))) {
                throw new BusinessException("PO_PR", "PR " + pl.get("doc_no") + " belum disetujui atau sudah selesai");
            }
            if (e.getValue().compareTo((BigDecimal) pl.get("remaining")) > 0) {
                throw BusinessException.of("PO_PR_QTY", "Qty PO melebihi sisa PR %s (%s)", pl.get("doc_no"),
                        ((BigDecimal) pl.get("remaining")).stripTrailingZeros().toPlainString());
            }
        }
        if (singleSource(d) && (d.getSingleSourceReason() == null || d.getSingleSourceReason().isBlank())) {
            throw BusinessException.of("PO_RFQ", "PO di atas Rp %,.0f wajib melalui perbandingan ≥ %s penawaran (PRC-06) atau diberi alasan pemasok tunggal",
                    fin.paramNum("RFQ_THRESHOLD", "50000000"), fin.paramNum("RFQ_MIN_QUOTES", "3").toPlainString());
        }
    }

    /** Terbit: anggaran beban terkunci sebagai komitmen; qty dipesan di PR bertambah, PR selesai bila terpenuhi. */
    @Override
    public void onApproved(PurchaseOrder d) {
        fin.commit(d.getPlantId(), "PO", d.getId(), d.getDocNo(), d.getDocDate(), costs(d));
        applyToPr(d, BigDecimal.ONE);
    }

    void applyToPr(PurchaseOrder d, BigDecimal sign) {
        Map<Long, PurchaseRequisition> touched = new LinkedHashMap<>();
        for (PurchaseOrder.Line l : d.getLines()) {
            if (l.getPrLineId() == null) {
                continue;
            }
            Long prId = jdbc.queryForObject("SELECT pr_id FROM prc.pr_line WHERE id = ?", Long.class, l.getPrLineId());
            PurchaseRequisition pr = touched.computeIfAbsent(prId, k -> prs.findById(k).orElseThrow());
            pr.getLines().stream().filter(x -> x.getId().equals(l.getPrLineId())).findFirst().ifPresent(x -> {
                x.setQtyOrdered(x.getQtyOrdered().add(l.getQty().multiply(sign)));
                x.setClosed(x.getQtyOrdered().compareTo(x.getQty()) >= 0);
            });
        }
        for (PurchaseRequisition pr : touched.values()) {
            prs.save(pr);
            if (sign.signum() > 0 && pr.getStatus() == DocStatus.APPROVED && pr.getLines().stream().allMatch(PurchaseRequisition.Line::isClosed)) {
                workflow.markDone("PR", pr.getId(), "Semua baris sudah menjadi PO (terakhir " + d.getDocNo() + ")");
            } else if (sign.signum() < 0 && pr.getStatus() == DocStatus.DONE) {
                pr.setStatus(DocStatus.APPROVED);
                prs.save(pr);
            }
        }
    }

    /** Pembatalan PO terbit hanya bila belum ada penerimaan; komitmen anggaran dilepas, qty PR dikembalikan. */
    @Override
    public void onCancel(PurchaseOrder d) {
        if (d.getStatus() != DocStatus.APPROVED) {
            return;
        }
        if (d.getLines().stream().anyMatch(l -> l.getReceivedQty().signum() > 0)) {
            throw new BusinessException("PO_RECEIVED", "PO sudah ada penerimaan; gunakan Tutup PO untuk sisa yang tidak dikirim");
        }
        fin.release("PO", d.getId());
        applyToPr(d, BigDecimal.ONE.negate());
    }

    /** Subtotal, PPN (kecuali impor: PPN impor lewat PIB), total, nilai Rupiah, jenis PO. */
    void recalc(PurchaseOrder d) {
        if (d.getCurrencyCode() == null) {
            d.setCurrencyCode("IDR");
        }
        if (d.getExchangeRate() == null || d.getExchangeRate().signum() <= 0 || "IDR".equals(d.getCurrencyCode())) {
            d.setExchangeRate(prc.rate(d.getCurrencyCode(), d.getDocDate()));
        }
        if (d.getPartnerId() != null && d.getPaymentTermDays() == 0) {
            Integer term = jdbc.queryForObject("SELECT payment_term_days FROM sys.partner WHERE id = ?", Integer.class, d.getPartnerId());
            d.setPaymentTermDays(term == null ? 30 : term);
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean allService = !d.getLines().isEmpty();
        for (PurchaseOrder.Line l : d.getLines()) {
            if (l.getItemId() == null) {
                continue;
            }
            if ((l.getUnitPrice() == null || l.getUnitPrice().signum() == 0) && d.getPartnerId() != null) {
                l.setUnitPrice(nz(prc.contractPrice(d.getPartnerId(), l.getItemId(), d.getDocDate(), l.getQty())));
            }
            if (l.getDiscountPct() == null) l.setDiscountPct(BigDecimal.ZERO);
            if (l.getNeedDate() == null) l.setNeedDate(d.getDeliveryDate());
            if (l.getEta() == null) l.setEta(l.getNeedDate());
            l.setAmount(PrcSupport.net(l.getQty(), l.getUnitPrice(), l.getDiscountPct()));
            subtotal = subtotal.add(l.getAmount());
            allService &= "SVC".equals(prc.item(l.getItemId()).get("type"));
        }
        d.setKind(allService ? "SERVICE" : "GOODS");
        d.setSubtotal(subtotal);
        d.setPpnAmount(d.isWithPpn() && !d.isImportPo() ? money(fin.ppn(subtotal.multiply(d.getExchangeRate())).divide(d.getExchangeRate(), 2,
                RoundingMode.HALF_UP)) : BigDecimal.ZERO);
        d.setTotal(subtotal.add(d.getPpnAmount()));
        d.setTotalIdr(money(d.getTotal().multiply(d.getExchangeRate())));
    }

    /** Penerimaan barang (SCM-20) memperbarui qty diterima; PO selesai saat semua baris terpenuhi atau ditutup. */
    @EventListener
    public void onGoodsReceived(GoodsReceived e) {
        PurchaseOrder po = load(e.poId());
        for (GoodsReceived.Line gl : e.lines()) {
            po.getLines().stream().filter(l -> l.getId().equals(gl.poLineId())).findFirst().ifPresent(l -> receive(po, l, gl.qty(), gl.amount(), e.date()));
        }
        save(po);
        closeIfComplete(po, "Diterima penuh (" + e.grNo() + ")");
        if (po.getStatus() == DocStatus.DONE && e.lines().stream().anyMatch(l -> l.qty().signum() < 0)) {
            po.setStatus(DocStatus.APPROVED);
            save(po);
        }
    }

    void receive(PurchaseOrder po, PurchaseOrder.Line l, BigDecimal qty, BigDecimal amount, LocalDate date) {
        l.setReceivedQty(l.getReceivedQty().add(qty));
        if (qty.signum() > 0) {
            if (l.getFirstReceipt() == null) {
                l.setFirstReceipt(date);
            }
            l.setLastReceipt(date);
        }
        if (l.getAccountId() != null && l.getCostCenterId() != null) {
            fin.consume("PO", po.getId(), l.getCostCenterId(), l.getAccountId(), amount);
        }
    }

    void closeIfComplete(PurchaseOrder po, String message) {
        if (po.getStatus() == DocStatus.APPROVED
                && po.getLines().stream().allMatch(l -> l.isClosed() || l.getReceivedQty().compareTo(l.getQty()) >= 0)) {
            fin.release("PO", po.getId());
            workflow.markDone("PO", po.getId(), message);
        }
    }
}

@RestController
@RequestMapping("/api/prc/purchase-orders")
class PurchaseOrderController extends DocumentApi<PurchaseOrder> {

    private final PurchaseOrderHandler handler;
    private final PrcSupport prc;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;

    PurchaseOrderController(PurchaseOrderHandler handler, PurchaseOrderRepository repo, Support support, PrcSupport prc, JdbcTemplate jdbc,
                            PermissionService perm) {
        super(handler, repo, support, PurchaseOrder.class);
        this.handler = handler;
        this.prc = prc;
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "notes");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("kind", "subtotal", "ppnAmount", "total", "totalIdr", "closedReason");
    }

    @Override
    protected void apply(PurchaseOrder target, PurchaseOrder in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (PurchaseOrder.Line l : in.getLines()) {
            l.setId(null);
            l.setOrder(target);
            l.setLineNo(no++);
            l.setReceivedQty(BigDecimal.ZERO);
            l.setFirstReceipt(null);
            l.setLastReceipt(null);
            l.setClosed(false);
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(PurchaseOrder d, boolean isNew) {
        require(d.getPartnerId() != null, "PO_PARTNER", "Supplier wajib dipilih");
        if (d.getDeliveryDate() == null) {
            d.setDeliveryDate(d.getDocDate().plusDays(14));
        }
        for (PurchaseOrder.Line l : d.getLines()) {
            if (l.getPrLineId() != null && (l.getItemId() == null || l.getQty() == null)) {
                jdbc.query("SELECT item_id, qty - qty_ordered, need_date FROM prc.pr_line WHERE id = ?", rs -> {
                    if (l.getItemId() == null) l.setItemId(rs.getLong(1));
                    if (l.getQty() == null) l.setQty(rs.getBigDecimal(2));
                    if (l.getNeedDate() == null && rs.getDate(3) != null) l.setNeedDate(rs.getDate(3).toLocalDate());
                }, l.getPrLineId());
            }
            require(l.getItemId() != null && l.getQty() != null, "PO_LINE", "Setiap baris wajib berisi item & qty");
            if (l.getPrLineId() != null && (l.getCostCenterId() == null || l.getAccountId() == null)) {
                jdbc.query("SELECT cost_center_id, account_id FROM prc.pr_line WHERE id = ?", rs -> {
                    if (l.getCostCenterId() == null) l.setCostCenterId((Long) rs.getObject(1));
                    if (l.getAccountId() == null) l.setAccountId((Long) rs.getObject(2));
                }, l.getPrLineId());
            }
        }
        handler.recalc(d);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, PurchaseOrder d) {
        body.put("partnerName", prc.partnerName(d.getPartnerId()));
        body.put("singleSource", d.getTotalIdr() != null && handler.singleSource(d));
        if (body.get("lines") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                Map<String, Object> i = prc.item(((Number) m.get("itemId")).longValue());
                m.put("itemLabel", i.get("code") + " · " + i.get("name"));
                m.put("uom", i.get("uom"));
                m.put("itemType", i.get("type"));
                if (m.get("prLineId") != null) {
                    m.put("prNo", jdbc.queryForObject("SELECT r.doc_no FROM prc.pr_line l JOIN prc.pr r ON r.id = l.pr_id WHERE l.id = ?",
                            String.class, ((Number) m.get("prLineId")).longValue()));
                }
            }
        }
    }

    record CloseRequest(String reason) {
    }

    /** Tutup sisa PO yang tidak akan dikirim supplier; komitmen sisa dilepas. */
    @PostMapping("/{id}/close")
    @Transactional
    public Envelope close(@PathVariable Long id, @RequestBody CloseRequest req) {
        perm.require("PRC-07", Action.CANCEL);
        require(req.reason() != null && !req.reason().isBlank(), "REASON_REQUIRED", "Alasan penutupan wajib diisi");
        PurchaseOrder d = handler.load(id);
        require(d.getStatus() == DocStatus.APPROVED, "PO_STATE", "Hanya PO terbit yang bisa ditutup");
        d.getLines().forEach(l -> l.setClosed(true));
        d.setClosedReason(req.reason());
        handler.save(d);
        handler.closeIfComplete(d, "Sisa PO ditutup: " + req.reason());
        return envelope(handler.load(id));
    }

    record Eta(Long lineId, LocalDate eta, String followupNote) {
    }

    /** PRC-09: perbarui ETA & catatan follow-up supplier pada PO terbit. */
    @PutMapping("/{id}/eta")
    @Transactional
    public Envelope eta(@PathVariable Long id, @RequestBody List<Eta> etas) {
        perm.require("PRC-09", Action.EDIT);
        PurchaseOrder d = handler.load(id);
        require(d.getStatus() == DocStatus.APPROVED, "PO_STATE", "ETA hanya untuk PO terbit");
        for (Eta e : etas) {
            d.getLines().stream().filter(l -> l.getId().equals(e.lineId())).findFirst().ifPresent(l -> {
                l.setEta(e.eta());
                l.setFollowupNote(e.followupNote());
            });
        }
        handler.save(d);
        return envelope(d);
    }
}
