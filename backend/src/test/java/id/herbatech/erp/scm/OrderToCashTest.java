package id.herbatech.erp.scm;

import id.herbatech.erp.IntegrationTest;
import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order to Cash M2: pesanan (cek kredit) → surat jalan (pick FEFO lot Released, HPP) → faktur penjualan otomatis →
 * retur pelanggan ke karantina dengan nota kredit; ditambah opname & penyesuaian stok.
 */
class OrderToCashTest extends IntegrationTest {

    @Autowired SalesOrderController orders;
    @Autowired DeliveryController deliveries;
    @Autowired CustomerReturnController returns;
    @Autowired StockAdjustmentController adjustments;
    @Autowired InventoryService inventory;
    @Autowired DocumentWorkflowService workflow;

    private static Long idOf(DocumentApi.Envelope e) {
        return ((Number) e.doc().get("id")).longValue();
    }

    private Long item(String code) {
        return id("SELECT id FROM sys.item WHERE code = ?", code);
    }

    private Long bin(String wh, String bin) {
        return id("SELECT l.id FROM sys.location l JOIN sys.warehouse w ON w.id = l.warehouse_id WHERE w.code = ? AND l.bin_code = ?", wh, bin);
    }

    /** Stok barang jadi Released di gudang FG (seolah sudah diluluskan QA). */
    private Long releasedStock(String itemCode, String qty, String cost) {
        return as("scm.manager", () -> {
            Lot lot = inventory.createLot(item(itemCode), "TFG" + System.nanoTime() % 100000000, null, today().minusDays(5), today().plusDays(700), "TEST");
            lot.setQcStatus(Lot.QcStatus.RELEASED);
            inventory.move(new InventoryService.MoveCommand(InventoryService.MoveType.RECEIPT, item(itemCode), lot.getId(), null, bin("WH-FG", "C-01-02"),
                    new BigDecimal(qty), new BigDecimal(cost), today(), "TEST", 0L, "TEST", "Stok uji"));
            return lot.getId();
        });
    }

    /** Customer khusus test agar eksposur kredit tidak dipengaruhi test lain. */
    private Long customer(String limit) {
        return id("""
                INSERT INTO sys.partner (code, name, type, city, currency_code, payment_term_days, credit_limit)
                VALUES (?, 'Customer Uji', 'CUSTOMER', 'Jakarta', 'IDR', 30, ?) RETURNING id""", "CUS-T" + System.nanoTime() % 100000000,
                new BigDecimal(limit));
    }

    private Long order(Long customer, String qty, String price) {
        SalesOrder so = new SalesOrder();
        so.setPartnerId(customer);
        so.setCustomerPo("PO-CUST-" + System.nanoTime());
        SalesOrder.Line l = new SalesOrder.Line();
        l.setItemId(item("FG-SRP100"));
        l.setQty(new BigDecimal(qty));
        l.setUnitPrice(new BigDecimal(price));
        so.getLines().add(l);
        Long id = idOf(as("scm.manager", () -> orders.create(so)));
        asVoid("scm.manager", () -> workflow.submit("SO", id));
        return id;
    }

    @Test
    void orderShipsFefoCreatesInvoiceAndReturnIssuesCreditNote() {
        releasedStock("FG-SRP100", "300", "12000");
        Long soId = order(customer("1000000000"), "100", "25000");
        assertThat(status("scm.so", soId)).isEqualTo("APPROVED");

        Delivery dl = new Delivery();
        dl.setSoId(soId);
        Delivery.Line l = new Delivery.Line();
        l.setSoLineId(id("SELECT id FROM scm.so_line WHERE so_id = ?", soId));
        l.setQty(new BigDecimal("100"));
        dl.getLines().add(l);
        Long doId = idOf(as("scm.spv", () -> deliveries.create(dl)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scm.delivery_line WHERE delivery_id = ? AND lot_id IS NOT NULL", Long.class, doId))
                .isPositive();
        asVoid("scm.spv", () -> workflow.submit("DO", doId));
        assertThat(status("scm.delivery", doId)).isEqualTo("POSTED");
        assertThat(status("scm.so", soId)).isEqualTo("DONE");

        BigDecimal cost = jdbc.queryForObject("SELECT SUM(qty * unit_cost) FROM scm.delivery_line WHERE delivery_id = ?", BigDecimal.class, doId);
        BigDecimal cogs = jdbc.queryForObject("""
                SELECT SUM(l.debit) FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id JOIN fin.account a ON a.id = l.account_id
                WHERE e.source_doc_type = 'DO' AND e.source_doc_id = ? AND a.code = '5101'""", BigDecimal.class, doId);
        assertThat(cogs).isEqualByComparingTo(cost);

        Long invId = id("SELECT ar_invoice_id FROM scm.delivery WHERE id = ?", doId);
        Map<String, Object> inv = jdbc.queryForMap("SELECT status, subtotal, ppn_amount, source_type FROM fin.ar_invoice WHERE id = ?", invId);
        assertThat(inv.get("status")).isEqualTo("DRAFT");
        assertThat(inv.get("source_type")).isEqualTo("DO");
        assertThat((BigDecimal) inv.get("subtotal")).isEqualByComparingTo("2500000");
        asVoid("fin.manager", () -> workflow.submit("INV-AR", invId));
        asVoid("fin.manager", () -> workflow.post("INV-AR", invId, null));

        CustomerReturn crt = new CustomerReturn();
        crt.setDeliveryId(doId);
        crt.setReason("Kemasan penyok saat pengiriman");
        CustomerReturn.Line rl = new CustomerReturn.Line();
        rl.setDeliveryLineId(id("SELECT MIN(id) FROM scm.delivery_line WHERE delivery_id = ?", doId));
        rl.setQty(new BigDecimal("10"));
        crt.getLines().add(rl);
        Long crtId = idOf(as("scm.manager", () -> returns.create(crt)));
        asVoid("scm.manager", () -> workflow.submit("CRT", crtId));
        approveAs("scm.spv", "CRT", crtId);
        assertThat(status("scm.customer_return", crtId)).isEqualTo("POSTED");
        BigDecimal credit = jdbc.queryForObject("SELECT total FROM scm.customer_return WHERE id = ?", BigDecimal.class, crtId);
        assertThat(jdbc.queryForObject("SELECT received_amount FROM fin.ar_invoice WHERE id = ?", BigDecimal.class, invId)).isEqualByComparingTo(credit);
        assertThat(jdbc.queryForObject("""
                SELECT loc.is_quarantine FROM scm.stock_move m JOIN sys.location loc ON loc.id = m.to_loc_id
                WHERE m.ref_doc_type = 'CRT' AND m.ref_doc_id = ?""", Boolean.class, crtId)).isTrue();
    }

    @Test
    void orderAboveCreditLimitWaitsForFinance() {
        Long soId = order(customer("100000000"), "5000", "25000");
        assertThat(jdbc.queryForObject("SELECT credit_hold FROM scm.so WHERE id = ?", Boolean.class, soId)).isTrue();
        assertThat(status("scm.so", soId)).isEqualTo("SUBMITTED");
        approveAs("fin.manager", "SO", soId);
        assertThat(status("scm.so", soId)).isEqualTo("APPROVED");
    }

    @Test
    void adjustmentReducesStockWithApprovalAndJournal() {
        Long lot = releasedStock("FG-SRP100", "20", "12000");
        StockAdjustment adj = new StockAdjustment();
        adj.setReason("Rusak terkena air hujan di area muat");
        StockAdjustment.Line l = new StockAdjustment.Line();
        l.setItemId(item("FG-SRP100"));
        l.setLotId(lot);
        l.setLocationId(bin("WH-FG", "C-01-02"));
        l.setQtyDelta(new BigDecimal("-5"));
        adj.getLines().add(l);
        Long adjId = idOf(as("scm.manager", () -> adjustments.create(adj)));
        asVoid("scm.manager", () -> workflow.submit("ADJ", adjId));
        approveAs("scm.spv", "ADJ", adjId);
        assertThat(status("scm.stock_adjustment", adjId)).isEqualTo("POSTED");
        assertThat(inventory.onHand(item("FG-SRP100"), lot, bin("WH-FG", "C-01-02"))).isEqualByComparingTo("15");
        assertThat(jdbc.queryForObject("""
                SELECT SUM(l.debit) FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id JOIN fin.account a ON a.id = l.account_id
                WHERE e.source_doc_type = 'ADJ' AND e.source_doc_id = ? AND a.code = '6402'""", BigDecimal.class, adjId)).isEqualByComparingTo("60000");
    }
}
