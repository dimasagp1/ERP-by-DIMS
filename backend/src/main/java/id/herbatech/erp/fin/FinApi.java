package id.herbatech.erp.fin;

import id.herbatech.erp.shared.document.DocumentWorkflowService;
import id.herbatech.erp.shared.error.BusinessException;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pintu FIN untuk modul lain (PRC, SCM, PRE): akun persediaan per jenis item, PPN, cek kredit customer,
 * kontrol & komitmen anggaran, dan faktur penjualan yang dibentuk dari surat jalan.
 * Jurnal otomatis memakai {@link JournalPostingService}.
 */
@Service
public class FinApi {

    /** Satu baris biaya untuk kontrol anggaran (cost center × akun beban). */
    public record CostLine(Long costCenterId, Long accountId, BigDecimal amount) {
    }

    public record CreditStatus(boolean hold, String reason) {
    }

    /** Baris faktur penjualan dari surat jalan. */
    public record SalesLine(Long itemId, String description, BigDecimal qty, BigDecimal unitPrice, BigDecimal discountPct) {
    }

    private final JdbcTemplate jdbc;
    private final FinSupport fin;
    private final BudgetGate budget;
    private final CreditCheck credit;
    private final ArInvoiceHandler arHandler;
    private final DocumentWorkflowService workflow;
    private final JournalPostingService journals;

    FinApi(JdbcTemplate jdbc, FinSupport fin, BudgetGate budget, CreditCheck credit, ArInvoiceHandler arHandler,
           @Lazy DocumentWorkflowService workflow, JournalPostingService journals) {
        this.journals = journals;
        this.jdbc = jdbc;
        this.fin = fin;
        this.budget = budget;
        this.credit = credit;
        this.arHandler = arHandler;
        this.workflow = workflow;
    }

    // ------------------------------------------------------------------ akun & parameter

    public String param(String key, String def) {
        return fin.param(key, def);
    }

    public BigDecimal paramNum(String key, String def) {
        return fin.paramNum(key, def);
    }

    public Long accountId(String code) {
        return jdbc.queryForList("SELECT id FROM fin.account WHERE code = ?", Long.class, code).stream().findFirst()
                .orElseThrow(() -> new BusinessException("ACCOUNT", "Akun " + code + " tidak ditemukan (FIN-02)"));
    }

    /** Akun persediaan sesuai jenis item (FIN-99 INV_ACC_*). */
    public Long inventoryAccount(String itemType) {
        return accountId(fin.param("INV_ACC_" + itemType, "1301"));
    }

    /** Akun beban pemakaian barang non-produksi (FIN-99 EXP_ACC_*). */
    public Long consumptionAccount(String itemType) {
        return accountId(fin.param("EXP_ACC_" + itemType, "6301"));
    }

    public BigDecimal ppn(BigDecimal base) {
        return fin.ppn(base);
    }

    public BigDecimal taxRate(String taxCode) {
        return fin.taxRate(taxCode);
    }

    public void validateAccount(Long accountId, Long costCenterId, String where) {
        fin.validateAccount(accountId, costCenterId, where);
    }

    // ------------------------------------------------------------------ kredit customer (FIN aturan 6)

    /**
     * Cek limit kredit & piutang lewat jatuh tempo untuk pesanan/pengiriman baru.
     * {@code pendingOrders} = nilai pesanan/pengiriman lain yang belum menjadi faktur.
     */
    public CreditStatus credit(Long partnerId, BigDecimal newAmount, BigDecimal pendingOrders, LocalDate onDate) {
        CreditCheck.Result r = credit.check(partnerId, null, newAmount.add(pendingOrders == null ? BigDecimal.ZERO : pendingOrders), onDate);
        return new CreditStatus(r.hold(), r.reason());
    }

    // ------------------------------------------------------------------ anggaran (FIN-51)

    /** true bila melebihi sisa anggaran pada mode WARN; melempar error pada mode BLOCK. */
    public boolean overBudget(Long plantId, LocalDate date, List<CostLine> costs) {
        return budget.overBudget(plantId, date, costs.stream()
                .filter(c -> c.accountId() != null && c.amount() != null)
                .map(c -> new BudgetGate.Cost(c.costCenterId(), c.accountId(), c.amount())).toList());
    }

    /** Kunci anggaran sebagai komitmen terbuka (PO disetujui). Hanya akun beban ber-cost center. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commit(Long plantId, String docType, Long docId, String docNo, LocalDate date, List<CostLine> costs) {
        String period = "%d%02d".formatted(date.getYear(), date.getMonthValue());
        costs.stream().filter(c -> c.costCenterId() != null && c.accountId() != null && c.amount() != null && c.amount().signum() > 0)
                .collect(Collectors.groupingBy(c -> c.costCenterId() + ":" + c.accountId(),
                        Collectors.reducing(BigDecimal.ZERO, CostLine::amount, BigDecimal::add)))
                .forEach((key, amount) -> {
                    String[] p = key.split(":");
                    Long acc = Long.valueOf(p[1]);
                    if ("EXPENSE".equals(jdbc.queryForObject("SELECT type FROM fin.account WHERE id = ?", String.class, acc))) {
                        jdbc.update("""
                                INSERT INTO fin.budget_commitment (plant_id, source_doc_type, source_doc_id, source_doc_no, cost_center_id,
                                                                   account_id, period, amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                                plantId, docType, docId, docNo, Long.valueOf(p[0]), acc, period, amount.setScale(2, RoundingMode.HALF_UP));
                    }
                });
    }

    /** Komitmen terpakai sebagian saat jasa/barang diterima dan beban diakui. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(String docType, Long docId, Long costCenterId, Long accountId, BigDecimal amount) {
        jdbc.update("""
                UPDATE fin.budget_commitment SET amount = GREATEST(amount - ?, 0),
                       state = CASE WHEN amount - ? <= 0 THEN 'CONSUMED' ELSE state END
                WHERE source_doc_type = ? AND source_doc_id = ? AND cost_center_id = ? AND account_id = ? AND state = 'OPEN'""",
                amount, amount, docType, docId, costCenterId, accountId);
    }

    /** Lepas sisa komitmen (PO dibatalkan atau ditutup). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(String docType, Long docId) {
        jdbc.update("UPDATE fin.budget_commitment SET state = 'RELEASED' WHERE source_doc_type = ? AND source_doc_id = ? AND state = 'OPEN'",
                docType, docId);
    }

    // ------------------------------------------------------------------ nota kredit retur pelanggan (SCM-27)

    /**
     * Nota kredit atas faktur penjualan: retur penjualan + PPN keluaran dikurangi, piutang faktur berkurang.
     * Faktur harus sudah diposting dan sisa piutangnya cukup.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applySalesCreditNote(Long plantId, LocalDate date, Long arInvoiceId, BigDecimal subtotal, BigDecimal ppnAmount,
                                     String docType, Long docId, String docNo) {
        ArInvoice inv = arHandler.load(arInvoiceId);
        if (inv.getStatus() != id.herbatech.erp.shared.domain.DocStatus.POSTED) {
            throw new BusinessException("CN_INVOICE", "Faktur penjualan " + inv.getDocNo() + " belum diposting; nota kredit belum bisa dibuat");
        }
        BigDecimal total = subtotal.add(ppnAmount);
        if (total.compareTo(inv.outstanding()) > 0) {
            throw BusinessException.of("CN_OVER", "Nota kredit Rp %,.0f melebihi sisa piutang faktur %s (Rp %,.0f)", total, inv.getDocNo(),
                    inv.outstanding());
        }
        inv.setReceivedAmount(inv.getReceivedAmount().add(total));
        arHandler.save(inv);
        String desc = "Nota kredit " + docNo + " atas " + inv.getDocNo();
        journals.postIds(plantId, date, List.of(
                new JournalPostingService.IdLine(accountId("4102"), null, desc, subtotal, null),
                new JournalPostingService.IdLine(accountId("2106"), null, "PPN keluaran " + desc, ppnAmount, null),
                new JournalPostingService.IdLine(accountId("1201"), null, desc, null, total)), docType, docId, docNo, desc);
    }

    // ------------------------------------------------------------------ faktur penjualan dari surat jalan (SCM-26 → FIN-20)

    /** Draft faktur penjualan dibentuk sistem saat surat jalan diposting; Finance melengkapi nomor faktur pajak & memposting. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long createSalesInvoice(Long plantId, LocalDate date, Long partnerId, Long deliveryId, String deliveryNo, String customerPo,
                                   boolean withPpn, List<SalesLine> lines) {
        ArInvoice inv = new ArInvoice();
        inv.setDocDate(date);
        inv.setPartnerId(partnerId);
        inv.setDeliveryId(deliveryId);
        inv.setSourceType("DO");
        inv.setCustomerPo(customerPo);
        inv.setWithPpn(withPpn);
        inv.setDescription("Pengiriman " + deliveryNo);
        short no = 1;
        for (SalesLine s : lines) {
            ArInvoice.Line l = new ArInvoice.Line();
            l.setInvoice(inv);
            l.setLineNo(no++);
            l.setItemId(s.itemId());
            l.setDescription(s.description());
            l.setQty(s.qty());
            l.setUnitPrice(s.unitPrice());
            l.setDiscountPct(s.discountPct());
            inv.getLines().add(l);
        }
        ArInvoiceController.recalc(inv, jdbc, fin);
        return workflow.initSystemDraft(arHandler, inv, plantId, "Dibentuk otomatis dari surat jalan " + deliveryNo).getId();
    }
}
