package id.herbatech.erp.fin;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

interface ApInvoiceRepository extends DocumentRepository<ApInvoice> {
}

interface PaymentRepository extends DocumentRepository<Payment> {
}

// =====================================================================================================================
// FIN-10 Faktur supplier
// =====================================================================================================================

@Component
class ApInvoiceHandler implements DocumentHandler<ApInvoice> {

    private final ApInvoiceRepository repo;
    private final PaymentRepository payments;
    private final JournalPostingService journals;
    private final FinSupport fin;
    private final BudgetGate budget;
    private final JdbcTemplate jdbc;

    ApInvoiceHandler(ApInvoiceRepository repo, PaymentRepository payments, JournalPostingService journals, FinSupport fin,
                     BudgetGate budget, JdbcTemplate jdbc) {
        this.repo = repo;
        this.payments = payments;
        this.journals = journals;
        this.fin = fin;
        this.budget = budget;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "INV-AP"; }
    @Override public String periodModule() { return "AP"; }
    @Override public ApInvoice load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Faktur supplier", id)); }
    @Override public ApInvoice save(ApInvoice doc) { return repo.save(doc); }

    @Override
    public String summary(ApInvoice d) {
        return "%s · %s%s".formatted(fin.partnerName(d.getPartnerId()), d.getSupplierInvoiceNo(),
                d.getDescription() == null ? "" : " · " + d.getDescription());
    }

    @Override public BigDecimal amount(ApInvoice d) { return d.getTotal(); }

    @Override
    public Set<String> approvalFlags(ApInvoice d) {
        List<BudgetGate.Cost> costs = d.getLines().stream()
                .map(l -> new BudgetGate.Cost(l.getCostCenterId(), l.getAccountId(), l.getAmount())).toList();
        Set<String> flags = new HashSet<>();
        if (budget.overBudget(d.getPlantId(), d.getDocDate(), costs)) {
            flags.add("OVER_BUDGET");
        }
        BigDecimal tol = fin.paramNum("MATCH_TOLERANCE_PCT", "2");
        if (d.getLines().stream().anyMatch(l -> l.getPriceVarPct() != null && l.getPriceVarPct().abs().compareTo(tol) > 0)) {
            flags.add("PRICE_VARIANCE");
        }
        return flags;
    }

    @Override
    public void validateSubmit(ApInvoice d) {
        fin.requirePartner(d.getPartnerId(), "SUPPLIER", "EXPEDITION");
        if (d.getSupplierInvoiceNo() == null || d.getSupplierInvoiceNo().isBlank()) {
            throw new BusinessException("AP_INVNO", "Nomor faktur supplier wajib diisi");
        }
        if (d.getDueDate() == null || d.getDueDate().isBefore(d.getDocDate())) {
            throw new BusinessException("AP_DUE", "Jatuh tempo tidak boleh sebelum tanggal faktur");
        }
        if (d.getLines().isEmpty()) {
            throw new BusinessException("AP_LINES", "Isi minimal satu baris biaya");
        }
        for (ApInvoice.Line l : d.getLines()) {
            fin.validateAccount(l.getAccountId(), l.getCostCenterId(), "Baris " + l.getLineNo());
            if (l.getAmount().signum() <= 0) {
                throw new BusinessException("AP_LINE", "Baris " + l.getLineNo() + ": nilai harus lebih dari nol");
            }
        }
        if (d.isWithPpn() && (d.getTaxInvoiceNo() == null || d.getTaxInvoiceNo().isBlank())) {
            throw new BusinessException("AP_TAXNO", "Faktur dengan PPN wajib mencantumkan nomor faktur pajak (FIN-60)");
        }
        Long dup = jdbc.queryForObject("""
                SELECT count(*) FROM fin.ap_invoice WHERE partner_id = ? AND supplier_invoice_no = ? AND id <> ? AND status <> 'CANCELLED'""",
                Long.class, d.getPartnerId(), d.getSupplierInvoiceNo(), d.getId());
        if (dup != null && dup > 0) {
            throw new BusinessException("AP_DUP", "Faktur supplier " + d.getSupplierInvoiceNo() + " sudah pernah dicatat");
        }
        if (d.getPoId() != null) {
            threeWayMatch(d);
        }
        if (d.getAdvancePaymentId() != null) {
            Payment adv = advance(d);
            BigDecimal available = adv.getAmount().subtract(adv.getAdvanceUsed());
            if (d.getAdvanceApplied().compareTo(available) > 0 || d.getAdvanceApplied().compareTo(d.getPayable()) > 0) {
                throw BusinessException.of("AP_ADVANCE", "Uang muka yang dipotong melebihi sisa uang muka (Rp %,.0f) atau nilai hutang", available);
            }
        }
    }

    /** 3-way match: qty faktur <= qty diterima (GR/BAST) - qty sudah difakturkan, per baris PO milik supplier yang sama. */
    void threeWayMatch(ApInvoice d) {
        Long partner = jdbc.queryForObject("SELECT partner_id FROM prc.po WHERE id = ?", Long.class, d.getPoId());
        if (!partner.equals(d.getPartnerId())) {
            throw new BusinessException("AP_PO", "Supplier faktur berbeda dengan supplier PO");
        }
        for (ApInvoice.Line l : d.getLines()) {
            if (l.getPoLineId() == null) {
                throw new BusinessException("AP_PO_LINE", "Baris " + l.getLineNo() + ": faktur berbasis PO wajib merujuk baris PO");
            }
            BigDecimal received = jdbc.queryForObject("SELECT received_qty FROM prc.po_line WHERE id = ? AND po_id = ?", BigDecimal.class,
                    l.getPoLineId(), d.getPoId());
            BigDecimal invoiced = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(il.qty), 0) FROM fin.ap_invoice_line il JOIN fin.ap_invoice i ON i.id = il.invoice_id
                    WHERE il.po_line_id = ? AND i.id <> ? AND i.status IN ('SUBMITTED','APPROVED','POSTED')""", BigDecimal.class,
                    l.getPoLineId(), d.getId());
            if (l.getQty().compareTo(received.subtract(invoiced)) > 0) {
                throw BusinessException.of("AP_MATCH", "Baris %d: qty faktur %s melebihi qty diterima belum difakturkan (%s) — 3-way match",
                        l.getLineNo(), l.getQty().stripTrailingZeros().toPlainString(), received.subtract(invoiced).stripTrailingZeros().toPlainString());
            }
        }
    }

    private Payment advance(ApInvoice d) {
        Payment adv = payments.findById(d.getAdvancePaymentId()).orElseThrow(() -> new NotFoundException("Uang muka", d.getAdvancePaymentId()));
        if (!"ADVANCE".equals(adv.getKind()) || adv.getStatus() != DocStatus.POSTED || !adv.getPartnerId().equals(d.getPartnerId())) {
            throw new BusinessException("AP_ADVANCE", "Uang muka harus milik supplier yang sama dan sudah diposting");
        }
        return adv;
    }

    @Override
    public void onPost(ApInvoice d) {
        validateSubmit(d);
        String desc = "Faktur " + d.getSupplierInvoiceNo() + " · " + fin.partnerName(d.getPartnerId());
        List<JournalPostingService.IdLine> lines = new ArrayList<>();
        for (ApInvoice.Line l : d.getLines()) {
            String text = l.getDescription() == null ? desc : l.getDescription();
            if (l.getPoLineId() == null) {
                lines.add(new JournalPostingService.IdLine(l.getAccountId(), l.getCostCenterId(), text, l.getAmount(), null));
                continue;
            }
            // Hutang belum difakturkan dibalik sebesar nilai terima (harga PO); selisih harga ke selisih harga bahan / akun beban jasa.
            BigDecimal received = l.getQty().multiply(l.getPoPrice()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal variance = l.getAmount().subtract(received);
            lines.add(new JournalPostingService.IdLine(journals.accountId("2102"), null, text, received, null));
            if (variance.signum() != 0) {
                Map<String, Object> pl = jdbc.queryForMap("SELECT account_id, cost_center_id FROM prc.po_line WHERE id = ?", l.getPoLineId());
                Long acc = pl.get("account_id") == null ? journals.accountId("5201") : ((Number) pl.get("account_id")).longValue();
                Long cc = pl.get("account_id") == null || pl.get("cost_center_id") == null ? null : ((Number) pl.get("cost_center_id")).longValue();
                lines.add(new JournalPostingService.IdLine(acc, cc, "Selisih harga " + text, variance.signum() > 0 ? variance : null,
                        variance.signum() < 0 ? variance.negate() : null));
            }
        }
        lines.add(new JournalPostingService.IdLine(journals.accountId("1220"), null, "PPN masukan " + d.getTaxInvoiceNo(), d.getPpnAmount(), null));
        lines.add(new JournalPostingService.IdLine(journals.accountId("2101"), null, desc, null, d.getPayable()));
        if (d.getPphAmount().signum() > 0) {
            lines.add(new JournalPostingService.IdLine(journals.accountId(pphAccount(d.getPphTaxCode())), null,
                    d.getPphTaxCode() + " " + d.getSupplierInvoiceNo(), null, d.getPphAmount()));
        }
        if (d.getAdvanceApplied().signum() > 0) {
            Payment adv = advance(d);
            lines.add(new JournalPostingService.IdLine(journals.accountId("2101"), null, "Potong uang muka " + adv.getDocNo(), d.getAdvanceApplied(), null));
            lines.add(new JournalPostingService.IdLine(journals.accountId("1210"), null, "Potong uang muka " + adv.getDocNo(), null, d.getAdvanceApplied()));
            adv.setAdvanceUsed(adv.getAdvanceUsed().add(d.getAdvanceApplied()));
            payments.save(adv);
        }
        journals.postIds(d.getPlantId(), d.getDocDate(), lines, "INV-AP", d.getId(), d.getDocNo(), desc);
    }

    @Override
    public ApInvoice reverse(ApInvoice d, LocalDate date, String reason) {
        if (d.getPaidAmount().signum() > 0) {
            throw new BusinessException("AP_PAID", "Faktur sudah dibayar sebagian/penuh; batalkan pembayarannya dulu");
        }
        journals.reverseFor("INV-AP", d.getId(), date, reason);
        if (d.getAdvanceApplied().signum() > 0) {
            Payment adv = advance(d);
            adv.setAdvanceUsed(adv.getAdvanceUsed().subtract(d.getAdvanceApplied()));
            payments.save(adv);
        }
        return d;
    }

    String pphAccount(String taxCode) {
        String type = jdbc.queryForList("SELECT type FROM sys.tax_code WHERE code = ?", String.class, taxCode).stream().findFirst().orElse("PPH23");
        return "PPH4_2".equals(type) ? "2108" : "2105";
    }
}

@RestController
@RequestMapping("/api/fin/ap-invoices")
class ApInvoiceController extends DocumentApi<ApInvoice> {

    private final FinSupport fin;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;

    ApInvoiceController(ApInvoiceHandler handler, ApInvoiceRepository repo, Support support, FinSupport fin, JdbcTemplate jdbc,
                        PermissionService perm) {
        super(handler, repo, support, ApInvoice.class);
        this.fin = fin;
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "supplierInvoiceNo", "taxInvoiceNo", "description");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("subtotal", "ppnAmount", "pphAmount", "total", "payable", "paidAmount", "taxCredited");
    }

    @Override
    protected void apply(ApInvoice target, ApInvoice in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (ApInvoice.Line l : in.getLines()) {
            l.setId(null);
            l.setInvoice(target);
            l.setLineNo(no++);
            target.getLines().add(l);
        }
    }

    /** Hitung ulang subtotal, PPN (DPP nilai lain), PPh dipotong, total, hutang. */
    @Override
    protected void beforeSave(ApInvoice d, boolean isNew) {
        if (d.getPoId() != null) {
            d.setSourceType("PO");
            d.setPartnerId(jdbc.queryForObject("SELECT partner_id FROM prc.po WHERE id = ?", Long.class, d.getPoId()));
            Long grni = jdbc.queryForObject("SELECT id FROM fin.account WHERE code = '2102'", Long.class);
            for (ApInvoice.Line l : d.getLines()) {
                require(l.getPoLineId() != null, "AP_PO_LINE", "Faktur berbasis PO: setiap baris merujuk baris PO");
                Map<String, Object> pl = jdbc.queryForMap("""
                        SELECT l.unit_price * (1 - l.discount_pct / 100) * p.exchange_rate AS price, i.name, l.item_id
                        FROM prc.po_line l JOIN prc.po p ON p.id = l.po_id JOIN sys.item i ON i.id = l.item_id WHERE l.id = ?""", l.getPoLineId());
                BigDecimal poPrice = ((BigDecimal) pl.get("price")).setScale(4, RoundingMode.HALF_UP);
                l.setPoPrice(poPrice);
                l.setAccountId(grni);
                l.setCostCenterId(null);
                if (l.getDescription() == null || l.getDescription().isBlank()) {
                    l.setDescription((String) pl.get("name"));
                }
                if (l.getUnitPrice() == null || l.getUnitPrice().signum() == 0) {
                    l.setUnitPrice(poPrice);
                }
                l.setPriceVarPct(poPrice.signum() == 0 ? BigDecimal.ZERO
                        : l.getUnitPrice().subtract(poPrice).multiply(BigDecimal.valueOf(100)).divide(poPrice, 4, RoundingMode.HALF_UP));
            }
        } else {
            d.setSourceType("NON_PO");
            d.getLines().forEach(l -> {
                l.setPoLineId(null);
                l.setPoPrice(null);
                l.setPriceVarPct(null);
            });
        }
        require(d.getPartnerId() != null, "AP_PARTNER", "Supplier wajib dipilih");
        if (d.getDueDate() == null) {
            Integer term = jdbc.queryForObject("SELECT payment_term_days FROM sys.partner WHERE id = ?", Integer.class, d.getPartnerId());
            d.setDueDate(d.getDocDate().plusDays(term == null ? 30 : term));
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ApInvoice.Line l : d.getLines()) {
            if (l.getQty() == null) l.setQty(BigDecimal.ONE);
            if (l.getUnitPrice() == null) l.setUnitPrice(BigDecimal.ZERO);
            l.setAmount(l.getQty().multiply(l.getUnitPrice()).setScale(2, RoundingMode.HALF_UP));
            subtotal = subtotal.add(l.getAmount());
        }
        d.setSubtotal(subtotal);
        d.setPpnAmount(d.isWithPpn() ? fin.ppn(subtotal) : BigDecimal.ZERO);
        BigDecimal pph = d.getPphTaxCode() == null || d.getPphTaxCode().isBlank() ? BigDecimal.ZERO
                : subtotal.multiply(fin.taxRate(d.getPphTaxCode())).setScale(0, RoundingMode.HALF_UP);
        d.setPphAmount(pph);
        d.setTotal(subtotal.add(d.getPpnAmount()));
        d.setPayable(d.getTotal().subtract(pph));
        if (d.getAdvanceApplied() == null || d.getAdvancePaymentId() == null) {
            d.setAdvanceApplied(BigDecimal.ZERO);
        }
    }

    @Override
    protected void enrich(Map<String, Object> body, ApInvoice d) {
        body.put("partnerName", fin.partnerName(d.getPartnerId()));
        body.put("outstanding", d.outstanding());
        body.put("overdue", d.getStatus() == DocStatus.POSTED && d.outstanding().signum() > 0 && d.getDueDate().isBefore(LocalDate.now()));
    }

    /** Baris PO yang sudah diterima (GR/BAST) tetapi belum difakturkan — bahan faktur berbasis PO (3-way match). */
    @GetMapping("/po-lines")
    public List<Map<String, Object>> poLines(@RequestParam Long poId) {
        perm.require("FIN-10", Action.VIEW);
        return jdbc.queryForList("""
                SELECT l.id AS po_line_id, l.item_id, i.code || ' · ' || i.name AS item_label, l.qty AS ordered, l.received_qty,
                       COALESCE((SELECT SUM(il.qty) FROM fin.ap_invoice_line il JOIN fin.ap_invoice x ON x.id = il.invoice_id
                                 WHERE il.po_line_id = l.id AND x.status IN ('SUBMITTED','APPROVED','POSTED')), 0) AS invoiced,
                       l.unit_price * (1 - l.discount_pct / 100) * p.exchange_rate AS po_price
                FROM prc.po_line l JOIN prc.po p ON p.id = l.po_id JOIN sys.item i ON i.id = l.item_id
                WHERE l.po_id = ? AND l.received_qty > 0 ORDER BY l.line_no""", poId);
    }

    record OpenInvoice(Long id, String docNo, String supplierInvoiceNo, LocalDate docDate, LocalDate dueDate, BigDecimal payable,
                       BigDecimal outstanding) {
    }

    /** Faktur terposting yang masih punya sisa hutang (untuk alokasi pembayaran). */
    @GetMapping("/open")
    public List<OpenInvoice> open(@RequestParam Long partnerId) {
        perm.require("FIN-12", Action.VIEW);
        return jdbc.query("""
                SELECT id, doc_no, supplier_invoice_no, doc_date, due_date, payable, payable - advance_applied - paid_amount AS outstanding
                FROM fin.ap_invoice WHERE partner_id = ? AND plant_id = ? AND status = 'POSTED'
                  AND payable - advance_applied - paid_amount > 0 ORDER BY due_date""",
                (rs, i) -> new OpenInvoice(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDate(4).toLocalDate(),
                        rs.getDate(5).toLocalDate(), rs.getBigDecimal(6), rs.getBigDecimal(7)), partnerId, UserContext.plantId());
    }
}

// =====================================================================================================================
// FIN-11/12 Pembayaran hutang & uang muka
// =====================================================================================================================

@Component
class PaymentHandler implements DocumentHandler<Payment> {

    private final PaymentRepository repo;
    private final ApInvoiceRepository invoices;
    private final JournalPostingService journals;
    private final FinSupport fin;

    PaymentHandler(PaymentRepository repo, ApInvoiceRepository invoices, JournalPostingService journals, FinSupport fin) {
        this.repo = repo;
        this.invoices = invoices;
        this.journals = journals;
        this.fin = fin;
    }

    @Override public String docType() { return "PAY"; }
    @Override public String periodModule() { return "AP"; }
    @Override public Payment load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Pembayaran", id)); }
    @Override public Payment save(Payment doc) { return repo.save(doc); }

    @Override
    public String summary(Payment d) {
        return "%s %s · %d faktur".formatted("ADVANCE".equals(d.getKind()) ? "Uang muka" : "Bayar",
                fin.partnerName(d.getPartnerId()), d.getAllocations().size());
    }

    @Override public BigDecimal amount(Payment d) { return d.getAmount(); }

    @Override
    public void validateSubmit(Payment d) {
        fin.requirePartner(d.getPartnerId(), "SUPPLIER", "EXPEDITION");
        fin.glAccountOfBank(d.getBankAccountId());
        if (d.getAmount().signum() <= 0) {
            throw new BusinessException("PAY_AMOUNT", "Nilai pembayaran harus lebih dari nol");
        }
        if ("AP".equals(d.getKind())) {
            if (d.getAllocations().isEmpty()) {
                throw new BusinessException("PAY_ALLOC", "Pilih faktur yang dibayar");
            }
            Set<Long> seen = new HashSet<>();
            for (Payment.Allocation a : d.getAllocations()) {
                if (!seen.add(a.getInvoiceId())) {
                    throw new BusinessException("PAY_DUP", "Faktur yang sama dialokasikan dua kali");
                }
                ApInvoice inv = invoices.findById(a.getInvoiceId()).orElseThrow(() -> new NotFoundException("Faktur", a.getInvoiceId()));
                if (inv.getStatus() != DocStatus.POSTED || !inv.getPartnerId().equals(d.getPartnerId())) {
                    throw new BusinessException("PAY_INV", "Faktur " + inv.getDocNo() + " belum diposting atau milik supplier lain");
                }
                if (a.getAmount().compareTo(inv.outstanding()) > 0) {
                    throw BusinessException.of("PAY_OVER", "Alokasi ke %s melebihi sisa hutang Rp %,.0f", inv.getDocNo(), inv.outstanding());
                }
            }
        }
    }

    @Override
    public void onPost(Payment d) {
        validateSubmit(d);
        Long bank = fin.glAccountOfBank(d.getBankAccountId());
        String desc = summary(d) + (d.getReference() == null ? "" : " · " + d.getReference());
        Long debit = journals.accountId("ADVANCE".equals(d.getKind()) ? "1210" : "2101");
        journals.postIds(d.getPlantId(), d.getDocDate(), List.of(
                new JournalPostingService.IdLine(debit, null, desc, d.getAmount(), null),
                new JournalPostingService.IdLine(bank, null, desc, null, d.getAmount())), "PAY", d.getId(), d.getDocNo(), desc);
        for (Payment.Allocation a : d.getAllocations()) {
            ApInvoice inv = invoices.findById(a.getInvoiceId()).orElseThrow();
            inv.setPaidAmount(inv.getPaidAmount().add(a.getAmount()));
            invoices.save(inv);
        }
    }

    @Override
    public Payment reverse(Payment d, LocalDate date, String reason) {
        if (d.isReconciled()) {
            throw new BusinessException("PAY_RECON", "Pembayaran sudah direkonsiliasi dengan mutasi bank; lepas pencocokannya dulu (FIN-31)");
        }
        if (d.getAdvanceUsed().signum() > 0) {
            throw new BusinessException("PAY_ADV_USED", "Uang muka sudah dipotongkan ke faktur");
        }
        journals.reverseFor("PAY", d.getId(), date, reason);
        for (Payment.Allocation a : d.getAllocations()) {
            ApInvoice inv = invoices.findById(a.getInvoiceId()).orElseThrow();
            inv.setPaidAmount(inv.getPaidAmount().subtract(a.getAmount()));
            invoices.save(inv);
        }
        return d;
    }
}

@RestController
@RequestMapping("/api/fin/payments")
class PaymentController extends DocumentApi<Payment> {

    private final FinSupport fin;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;

    PaymentController(PaymentHandler handler, PaymentRepository repo, Support support, FinSupport fin, JdbcTemplate jdbc,
                      PermissionService perm) {
        super(handler, repo, support, Payment.class);
        this.fin = fin;
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "reference", "description");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("advanceUsed", "reconciled");
    }

    @Override
    protected void apply(Payment target, Payment in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getAllocations().clear();
        short no = 1;
        for (Payment.Allocation a : in.getAllocations()) {
            a.setId(null);
            a.setPayment(target);
            a.setLineNo(no++);
            target.getAllocations().add(a);
        }
    }

    @Override
    protected void beforeSave(Payment d, boolean isNew) {
        require(List.of("AP", "ADVANCE").contains(d.getKind()), "PAY_KIND", "Jenis pembayaran tidak dikenal");
        require(d.getPartnerId() != null && d.getBankAccountId() != null, "PAY_REQ", "Supplier dan rekening bank wajib dipilih");
        if ("ADVANCE".equals(d.getKind())) {
            d.getAllocations().clear();
            require(d.getAmount() != null && d.getAmount().signum() > 0, "PAY_AMOUNT", "Nilai uang muka wajib diisi");
        } else {
            for (Payment.Allocation a : d.getAllocations()) {
                require(a.getInvoiceId() != null && a.getAmount() != null && a.getAmount().signum() > 0, "PAY_ALLOC",
                        "Setiap alokasi wajib memilih faktur dan nilai > 0");
            }
            d.setAmount(d.getAllocations().stream().map(Payment.Allocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, Payment d) {
        body.put("partnerName", fin.partnerName(d.getPartnerId()));
        body.put("bankAccountName", jdbc.queryForList("SELECT code || ' · ' || name FROM fin.bank_account WHERE id = ?", String.class,
                d.getBankAccountId()).stream().findFirst().orElse(null));
        if (body.get("allocations") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                Long inv = ((Number) m.get("invoiceId")).longValue();
                jdbc.query("SELECT doc_no, supplier_invoice_no, due_date FROM fin.ap_invoice WHERE id = ?", rs -> {
                    m.put("invoiceDocNo", rs.getString(1));
                    m.put("supplierInvoiceNo", rs.getString(2));
                    m.put("dueDate", rs.getDate(3).toLocalDate());
                }, inv);
            }
        }
        if ("ADVANCE".equals(d.getKind())) {
            body.put("advanceAvailable", d.getAmount().subtract(d.getAdvanceUsed()));
        }
    }

    record OpenAdvance(Long id, String docNo, LocalDate docDate, BigDecimal amount, BigDecimal available) {
    }

    /** Uang muka supplier yang masih bisa dipotongkan ke faktur. */
    @GetMapping("/advances")
    public List<OpenAdvance> advances(@RequestParam Long partnerId) {
        perm.require("FIN-10", Action.VIEW);
        return jdbc.query("""
                SELECT id, doc_no, doc_date, amount, amount - advance_used FROM fin.payment
                WHERE kind = 'ADVANCE' AND status = 'POSTED' AND partner_id = ? AND plant_id = ? AND amount > advance_used ORDER BY doc_date""",
                (rs, i) -> new OpenAdvance(rs.getLong(1), rs.getString(2), rs.getDate(3).toLocalDate(), rs.getBigDecimal(4),
                        rs.getBigDecimal(5)), partnerId, UserContext.plantId());
    }
}
