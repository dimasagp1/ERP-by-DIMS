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
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

interface ArInvoiceRepository extends DocumentRepository<ArInvoice> {
}

interface ReceiptRepository extends DocumentRepository<Receipt> {
}

/** Cek kredit customer (FIN-22): limit kredit dan piutang lewat N hari (PRD FIN aturan 6, SCM aturan 7). */
@Component
class CreditCheck {

    record Result(boolean hold, String reason, BigDecimal limit, BigDecimal exposure, BigDecimal overdue) {
    }

    private final JdbcTemplate jdbc;
    private final FinSupport fin;

    CreditCheck(JdbcTemplate jdbc, FinSupport fin) {
        this.jdbc = jdbc;
        this.fin = fin;
    }

    Result check(Long partnerId, Long excludeInvoiceId, BigDecimal newAmount, LocalDate onDate) {
        BigDecimal limit = jdbc.queryForList("SELECT credit_limit FROM sys.partner WHERE id = ?", BigDecimal.class, partnerId)
                .stream().findFirst().orElse(null);
        BigDecimal exposure = jdbc.queryForObject("""
                SELECT COALESCE(SUM(total - received_amount), 0) FROM fin.ar_invoice
                WHERE partner_id = ? AND id <> ? AND status IN ('SUBMITTED','APPROVED','POSTED')""",
                BigDecimal.class, partnerId, excludeInvoiceId == null ? -1L : excludeInvoiceId);
        int days = Integer.parseInt(fin.param("AR_OVERDUE_DAYS", "60"));
        BigDecimal overdue = jdbc.queryForObject("""
                SELECT COALESCE(SUM(total - received_amount), 0) FROM fin.ar_invoice
                WHERE partner_id = ? AND status = 'POSTED' AND total > received_amount AND due_date < ?""",
                BigDecimal.class, partnerId, Date.valueOf(onDate.minusDays(days)));
        List<String> reasons = new ArrayList<>();
        BigDecimal after = exposure.add(newAmount);
        if (limit != null && after.compareTo(limit) > 0) {
            reasons.add("melewati limit kredit (Rp %,.0f dari limit Rp %,.0f)".formatted(after, limit));
        }
        if (overdue.signum() > 0) {
            reasons.add("ada piutang lewat %d hari Rp %,.0f".formatted(days, overdue));
        }
        return new Result(!reasons.isEmpty(), reasons.isEmpty() ? null : "Customer " + String.join(" dan ", reasons),
                limit, exposure, overdue);
    }
}

// =====================================================================================================================
// FIN-20 Faktur penjualan
// =====================================================================================================================

@Component
class ArInvoiceHandler implements DocumentHandler<ArInvoice> {

    private final ArInvoiceRepository repo;
    private final JournalPostingService journals;
    private final FinSupport fin;
    private final CreditCheck credit;

    ArInvoiceHandler(ArInvoiceRepository repo, JournalPostingService journals, FinSupport fin, CreditCheck credit) {
        this.repo = repo;
        this.journals = journals;
        this.fin = fin;
        this.credit = credit;
    }

    @Override public String docType() { return "INV-AR"; }
    @Override public String periodModule() { return "AR"; }
    @Override public ArInvoice load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Faktur penjualan", id)); }
    @Override public ArInvoice save(ArInvoice doc) { return repo.save(doc); }

    @Override
    public String summary(ArInvoice d) {
        return fin.partnerName(d.getPartnerId()) + (d.getDescription() == null ? "" : " · " + d.getDescription())
                + (d.isCreditHold() ? " · TERTAHAN" : "");
    }

    @Override public BigDecimal amount(ArInvoice d) { return d.getTotal(); }

    /** Faktur tertahan butuh approval Manager FIN; tanpa masalah kredit langsung disetujui. */
    @Override
    public Set<String> approvalFlags(ArInvoice d) {
        return d.isCreditHold() ? Set.of("CREDIT_HOLD") : Set.of();
    }

    @Override
    public void validateSubmit(ArInvoice d) {
        fin.requirePartner(d.getPartnerId(), "CUSTOMER");
        if (d.getLines().isEmpty()) {
            throw new BusinessException("AR_LINES", "Isi minimal satu baris");
        }
        for (ArInvoice.Line l : d.getLines()) {
            fin.validateAccount(l.getRevenueAccountId(), null, "Baris " + l.getLineNo());
            if (l.getAmount().signum() <= 0) {
                throw new BusinessException("AR_LINE", "Baris " + l.getLineNo() + ": nilai harus lebih dari nol");
            }
        }
        if (d.getDueDate() == null || d.getDueDate().isBefore(d.getDocDate())) {
            throw new BusinessException("AR_DUE", "Jatuh tempo tidak boleh sebelum tanggal faktur");
        }
        CreditCheck.Result r = credit.check(d.getPartnerId(), d.getId(), d.getTotal(), d.getDocDate());
        d.setCreditHold(r.hold());
        d.setHoldReason(r.reason());
    }

    @Override
    public void onPost(ArInvoice d) {
        String desc = "Faktur " + d.getDocNo() + " · " + fin.partnerName(d.getPartnerId());
        List<JournalPostingService.IdLine> lines = new ArrayList<>();
        lines.add(new JournalPostingService.IdLine(journals.accountId("1201"), null, desc, d.getTotal(), null));
        d.getLines().forEach(l -> lines.add(new JournalPostingService.IdLine(l.getRevenueAccountId(), null,
                l.getDescription() == null ? desc : l.getDescription(), null, l.getAmount())));
        lines.add(new JournalPostingService.IdLine(journals.accountId("2106"), null, "PPN keluaran " + d.getDocNo(), null, d.getPpnAmount()));
        journals.postIds(d.getPlantId(), d.getDocDate(), lines, "INV-AR", d.getId(), d.getDocNo(), desc);
    }

    @Override
    public ArInvoice reverse(ArInvoice d, LocalDate date, String reason) {
        if (d.getReceivedAmount().signum() > 0) {
            throw new BusinessException("AR_PAID", "Faktur sudah menerima pelunasan; batalkan penerimaannya dulu");
        }
        journals.reverseFor("INV-AR", d.getId(), date, reason);
        return d;
    }
}

@RestController
@RequestMapping("/api/fin/ar-invoices")
class ArInvoiceController extends DocumentApi<ArInvoice> {

    private final FinSupport fin;
    private final CreditCheck credit;
    private final JdbcTemplate jdbc;
    private final PermissionService perm;

    ArInvoiceController(ArInvoiceHandler handler, ArInvoiceRepository repo, Support support, FinSupport fin, CreditCheck credit,
                        JdbcTemplate jdbc, PermissionService perm) {
        super(handler, repo, support, ArInvoice.class);
        this.fin = fin;
        this.credit = credit;
        this.jdbc = jdbc;
        this.perm = perm;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "taxInvoiceNo", "customerPo", "description");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("subtotal", "ppnAmount", "total", "receivedAmount", "creditHold", "holdReason", "sourceType", "deliveryId", "taxReported");
    }

    @Override
    protected void apply(ArInvoice target, ArInvoice in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (ArInvoice.Line l : in.getLines()) {
            l.setId(null);
            l.setInvoice(target);
            l.setLineNo(no++);
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(ArInvoice d, boolean isNew) {
        require(d.getPartnerId() != null, "AR_PARTNER", "Customer wajib dipilih");
        require(d.getDeliveryId() == null || !d.getLines().isEmpty(), "AR_LINES", "Faktur dari surat jalan wajib punya baris");
        recalc(d, jdbc, fin);
    }

    /** Jatuh tempo bawaan, nilai baris (qty × harga − diskon), PPN (DPP nilai lain), total. */
    static void recalc(ArInvoice d, JdbcTemplate jdbc, FinSupport fin) {
        if (d.getDueDate() == null) {
            Integer term = jdbc.queryForObject("SELECT payment_term_days FROM sys.partner WHERE id = ?", Integer.class, d.getPartnerId());
            d.setDueDate(d.getDocDate().plusDays(term == null ? 30 : term));
        }
        Long sales = jdbc.queryForObject("SELECT id FROM fin.account WHERE code = '4101'", Long.class);
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ArInvoice.Line l : d.getLines()) {
            if (l.getQty() == null) l.setQty(BigDecimal.ONE);
            if (l.getUnitPrice() == null) l.setUnitPrice(BigDecimal.ZERO);
            if (l.getDiscountPct() == null) l.setDiscountPct(BigDecimal.ZERO);
            if (l.getRevenueAccountId() == null) l.setRevenueAccountId(sales);
            BigDecimal gross = l.getQty().multiply(l.getUnitPrice());
            l.setAmount(gross.subtract(gross.multiply(l.getDiscountPct()).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP))
                    .setScale(2, RoundingMode.HALF_UP));
            if ((l.getDescription() == null || l.getDescription().isBlank()) && l.getItemId() != null) {
                l.setDescription(jdbc.queryForObject("SELECT name FROM sys.item WHERE id = ?", String.class, l.getItemId()));
            }
            subtotal = subtotal.add(l.getAmount());
        }
        d.setSubtotal(subtotal);
        d.setPpnAmount(d.isWithPpn() ? fin.ppn(subtotal) : BigDecimal.ZERO);
        d.setTotal(subtotal.add(d.getPpnAmount()));
    }

    @Override
    protected void enrich(Map<String, Object> body, ArInvoice d) {
        body.put("partnerName", fin.partnerName(d.getPartnerId()));
        body.put("outstanding", d.outstanding());
        if (d.getStatus().isEditable() && d.getPartnerId() != null) {
            CreditCheck.Result r = credit.check(d.getPartnerId(), d.getId(), d.getTotal(), d.getDocDate());
            body.put("creditPreview", r);
        }
    }

    record OpenInvoice(Long id, String docNo, LocalDate docDate, LocalDate dueDate, BigDecimal total, BigDecimal outstanding) {
    }

    @GetMapping("/open")
    public List<OpenInvoice> open(@RequestParam Long partnerId) {
        perm.require("FIN-21", Action.VIEW);
        return jdbc.query("""
                SELECT id, doc_no, doc_date, due_date, total, total - received_amount FROM fin.ar_invoice
                WHERE partner_id = ? AND plant_id = ? AND status = 'POSTED' AND total > received_amount ORDER BY due_date""",
                (rs, i) -> new OpenInvoice(rs.getLong(1), rs.getString(2), rs.getDate(3).toLocalDate(), rs.getDate(4).toLocalDate(),
                        rs.getBigDecimal(5), rs.getBigDecimal(6)), partnerId, UserContext.plantId());
    }
}

// =====================================================================================================================
// FIN-21 Penerimaan pembayaran
// =====================================================================================================================

@Component
class ReceiptHandler implements DocumentHandler<Receipt> {

    private final ReceiptRepository repo;
    private final ArInvoiceRepository invoices;
    private final JournalPostingService journals;
    private final FinSupport fin;

    ReceiptHandler(ReceiptRepository repo, ArInvoiceRepository invoices, JournalPostingService journals, FinSupport fin) {
        this.repo = repo;
        this.invoices = invoices;
        this.journals = journals;
        this.fin = fin;
    }

    @Override public String docType() { return "RCV"; }
    @Override public String periodModule() { return "AR"; }
    @Override public Receipt load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Penerimaan", id)); }
    @Override public Receipt save(Receipt doc) { return repo.save(doc); }

    @Override
    public String summary(Receipt d) {
        return "Terima dari %s · %d faktur".formatted(fin.partnerName(d.getPartnerId()), d.getAllocations().size());
    }

    @Override public BigDecimal amount(Receipt d) { return d.getAmount(); }

    @Override
    public void validateSubmit(Receipt d) {
        fin.requirePartner(d.getPartnerId(), "CUSTOMER");
        fin.glAccountOfBank(d.getBankAccountId());
        if (d.getAllocations().isEmpty()) {
            throw new BusinessException("RCV_ALLOC", "Pilih faktur yang dilunasi");
        }
        Set<Long> seen = new HashSet<>();
        for (Receipt.Allocation a : d.getAllocations()) {
            if (!seen.add(a.getInvoiceId())) {
                throw new BusinessException("RCV_DUP", "Faktur yang sama dialokasikan dua kali");
            }
            ArInvoice inv = invoices.findById(a.getInvoiceId()).orElseThrow(() -> new NotFoundException("Faktur", a.getInvoiceId()));
            if (inv.getStatus() != DocStatus.POSTED || !inv.getPartnerId().equals(d.getPartnerId())) {
                throw new BusinessException("RCV_INV", "Faktur " + inv.getDocNo() + " belum diposting atau milik customer lain");
            }
            if (a.getAmount().compareTo(inv.outstanding()) > 0) {
                throw BusinessException.of("RCV_OVER", "Alokasi ke %s melebihi sisa piutang Rp %,.0f", inv.getDocNo(), inv.outstanding());
            }
        }
    }

    @Override
    public void onPost(Receipt d) {
        validateSubmit(d);
        String desc = summary(d) + (d.getReference() == null ? "" : " · " + d.getReference());
        journals.postIds(d.getPlantId(), d.getDocDate(), List.of(
                new JournalPostingService.IdLine(fin.glAccountOfBank(d.getBankAccountId()), null, desc, d.getAmount(), null),
                new JournalPostingService.IdLine(journals.accountId("1201"), null, desc, null, d.getAmount())),
                "RCV", d.getId(), d.getDocNo(), desc);
        for (Receipt.Allocation a : d.getAllocations()) {
            ArInvoice inv = invoices.findById(a.getInvoiceId()).orElseThrow();
            inv.setReceivedAmount(inv.getReceivedAmount().add(a.getAmount()));
            invoices.save(inv);
        }
    }

    @Override
    public Receipt reverse(Receipt d, LocalDate date, String reason) {
        if (d.isReconciled()) {
            throw new BusinessException("RCV_RECON", "Penerimaan sudah direkonsiliasi dengan mutasi bank; lepas pencocokannya dulu");
        }
        journals.reverseFor("RCV", d.getId(), date, reason);
        for (Receipt.Allocation a : d.getAllocations()) {
            ArInvoice inv = invoices.findById(a.getInvoiceId()).orElseThrow();
            inv.setReceivedAmount(inv.getReceivedAmount().subtract(a.getAmount()));
            invoices.save(inv);
        }
        return d;
    }
}

@RestController
@RequestMapping("/api/fin/receipts")
class ReceiptController extends DocumentApi<Receipt> {

    private final FinSupport fin;
    private final JdbcTemplate jdbc;

    ReceiptController(ReceiptHandler handler, ReceiptRepository repo, Support support, FinSupport fin, JdbcTemplate jdbc) {
        super(handler, repo, support, Receipt.class);
        this.fin = fin;
        this.jdbc = jdbc;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "reference", "description");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("reconciled");
    }

    @Override
    protected void apply(Receipt target, Receipt in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getAllocations().clear();
        short no = 1;
        for (Receipt.Allocation a : in.getAllocations()) {
            a.setId(null);
            a.setReceipt(target);
            a.setLineNo(no++);
            target.getAllocations().add(a);
        }
    }

    @Override
    protected void beforeSave(Receipt d, boolean isNew) {
        require(d.getPartnerId() != null && d.getBankAccountId() != null, "RCV_REQ", "Customer dan rekening bank wajib dipilih");
        for (Receipt.Allocation a : d.getAllocations()) {
            require(a.getInvoiceId() != null && a.getAmount() != null && a.getAmount().signum() > 0, "RCV_ALLOC",
                    "Setiap alokasi wajib memilih faktur dan nilai > 0");
        }
        d.setAmount(d.getAllocations().stream().map(Receipt.Allocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, Receipt d) {
        body.put("partnerName", fin.partnerName(d.getPartnerId()));
        body.put("bankAccountName", jdbc.queryForList("SELECT code || ' · ' || name FROM fin.bank_account WHERE id = ?", String.class,
                d.getBankAccountId()).stream().findFirst().orElse(null));
        if (body.get("allocations") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                jdbc.query("SELECT doc_no, due_date FROM fin.ar_invoice WHERE id = ?", rs -> {
                    m.put("invoiceDocNo", rs.getString(1));
                    m.put("dueDate", rs.getDate(2).toLocalDate());
                }, ((Number) m.get("invoiceId")).longValue());
            }
        }
    }
}
