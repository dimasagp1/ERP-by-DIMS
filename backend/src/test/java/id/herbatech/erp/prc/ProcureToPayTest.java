package id.herbatech.erp.prc;

import id.herbatech.erp.IntegrationTest;
import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Procure to Pay M2: PR → PO (harga kontrak, ASL, komitmen) → GR (lot karantina, jurnal GRNI) → faktur supplier 3-way match
 * (toleransi harga 2%) → hutang.
 */
class ProcureToPayTest extends IntegrationTest {

    @Autowired PurchaseRequisitionController requisitions;
    @Autowired PurchaseOrderController orders;
    @Autowired id.herbatech.erp.scm.ScmTestApi scm;
    @Autowired id.herbatech.erp.fin.FinTestApi fin;
    @Autowired DocumentWorkflowService workflow;

    private static Long idOf(DocumentApi.Envelope e) {
        return ((Number) e.doc().get("id")).longValue();
    }

    private Long item(String code) {
        return id("SELECT id FROM sys.item WHERE code = ?", code);
    }

    private Long partner(String code) {
        return id("SELECT id FROM sys.partner WHERE code = ?", code);
    }

    private Long approvedPo(String itemCode, String supplier, String qty) {
        PurchaseRequisition pr = new PurchaseRequisition();
        pr.setPurpose("Stok bahan uji");
        PurchaseRequisition.Line pl = new PurchaseRequisition.Line();
        pl.setItemId(item(itemCode));
        pl.setQty(new BigDecimal(qty));
        pl.setNeedDate(today().plusDays(10));
        pr.getLines().add(pl);
        Long prId = idOf(as("prc.staf", () -> requisitions.create(pr)));
        asVoid("prc.staf", () -> workflow.submit("PR", prId));
        approveAs("prc.manager", "PR", prId);
        assertThat(status("prc.pr", prId)).isEqualTo("APPROVED");

        PurchaseOrder po = new PurchaseOrder();
        po.setPartnerId(partner(supplier));
        PurchaseOrder.Line l = new PurchaseOrder.Line();
        l.setItemId(item(itemCode));
        l.setQty(new BigDecimal(qty));
        l.setPrLineId(id("SELECT id FROM prc.pr_line WHERE pr_id = ?", prId));
        po.getLines().add(l);
        var created = as("prc.staf", () -> orders.create(po));
        Long poId = idOf(created);
        asVoid("prc.staf", () -> workflow.submit("PO", poId));
        approveAs("prc.manager", "PO", poId);
        assertThat(status("prc.po", poId)).isEqualTo("APPROVED");
        assertThat(status("prc.pr", prId)).isEqualTo("DONE");
        return poId;
    }

    @Test
    void purchaseOrderUsesContractPriceAndRequiresApprovedSupplierList() {
        Long poId = approvedPo("RM-SIM-001", "SUP-0001", "100");
        BigDecimal price = jdbc.queryForObject("SELECT unit_price FROM prc.po_line WHERE po_id = ?", BigDecimal.class, poId);
        assertThat(price).isEqualByComparingTo("65000");

        PurchaseOrder bad = new PurchaseOrder();
        bad.setPartnerId(partner("SUP-0002"));
        PurchaseOrder.Line l = new PurchaseOrder.Line();
        l.setItemId(item("RM-SIM-001"));
        l.setQty(BigDecimal.TEN);
        l.setUnitPrice(new BigDecimal("60000"));
        bad.getLines().add(l);
        Long badId = idOf(as("prc.staf", () -> orders.create(bad)));
        assertThatThrownBy(() -> asVoid("prc.staf", () -> workflow.submit("PO", badId))).hasMessageContaining("Daftar Supplier Disetujui");
    }

    @Test
    void receiptGoesToQuarantineAndInvoiceIsThreeWayMatched() {
        Long poId = approvedPo("RM-SIM-002", "SUP-0001", "50");
        Long poLine = id("SELECT id FROM prc.po_line WHERE po_id = ?", poId);

        assertThatThrownBy(() -> scm.receive("scm.spv", poId, poLine, "52", "LOT-OVER")).hasMessageContaining("toleransi");
        Long grId = scm.receive("scm.spv", poId, poLine, "50", "SUPLOT-" + System.nanoTime());
        assertThat(status("scm.gr", grId)).isEqualTo("POSTED");
        Map<String, Object> lot = jdbc.queryForMap("""
                SELECT l.qc_status, loc.is_quarantine, q.qty, q.unit_cost FROM scm.gr_line gl JOIN scm.lot l ON l.id = gl.lot_id
                JOIN scm.stock_quant q ON q.lot_id = l.id JOIN sys.location loc ON loc.id = q.location_id WHERE gl.gr_id = ?""", grId);
        assertThat(lot.get("qc_status")).isEqualTo("QUARANTINE");
        assertThat(lot.get("is_quarantine")).isEqualTo(true);
        assertThat((BigDecimal) lot.get("unit_cost")).isEqualByComparingTo("70000");
        assertThat(status("prc.po", poId)).isEqualTo("DONE");
        BigDecimal grni = jdbc.queryForObject("""
                SELECT SUM(l.credit) FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id JOIN fin.account a ON a.id = l.account_id
                WHERE e.source_doc_type = 'GR' AND e.source_doc_id = ? AND a.code = '2102'""", BigDecimal.class, grId);
        assertThat(grni).isEqualByComparingTo("3500000");

        // Faktur 50 × 71.000 (selisih 1,43% ≤ 2%): tanpa approval selisih harga.
        Long invId = fin.invoice("fin.staf", poId, poLine, "50", "71000");
        assertThat(jdbc.queryForObject("SELECT price_var_pct FROM fin.ap_invoice_line WHERE invoice_id = ?", BigDecimal.class, invId))
                .isEqualByComparingTo("1.4286");
        asVoid("fin.staf", () -> workflow.submit("INV-AP", invId));
        approveAs("fin.manager", "INV-AP", invId);
        assertThat(status("fin.ap_invoice", invId)).isEqualTo("APPROVED");
        asVoid("fin.manager", () -> workflow.post("INV-AP", invId, null));
        Map<String, Object> je = jdbc.queryForMap("""
                SELECT SUM(l.debit) FILTER (WHERE a.code = '2102') AS grni, SUM(l.debit) FILTER (WHERE a.code = '5201') AS variance
                FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id JOIN fin.account a ON a.id = l.account_id
                WHERE e.source_doc_type = 'INV-AP' AND e.source_doc_id = ?""", invId);
        assertThat((BigDecimal) je.get("grni")).isEqualByComparingTo("3500000");
        assertThat((BigDecimal) je.get("variance")).isEqualByComparingTo("50000");

        // Qty faktur melebihi qty diterima belum difakturkan → ditolak (3-way match).
        Long extra = fin.invoice("fin.staf", poId, poLine, "1", "70000");
        assertThatThrownBy(() -> asVoid("fin.staf", () -> workflow.submit("INV-AP", extra))).hasMessageContaining("3-way match");
    }

    @Test
    void priceAboveToleranceNeedsProcurementApproval() {
        Long poId = approvedPo("RM-EXC-001", "SUP-0001", "40");
        Long poLine = id("SELECT id FROM prc.po_line WHERE po_id = ?", poId);
        scm.receive("scm.spv", poId, poLine, "40", "SUPLOT-" + System.nanoTime());
        Long invId = fin.invoice("fin.staf", poId, poLine, "40", "29400");
        asVoid("fin.staf", () -> workflow.submit("INV-AP", invId));
        approveAs("fin.manager", "INV-AP", invId);
        assertThat(status("fin.ap_invoice", invId)).isEqualTo("SUBMITTED");
        approveAs("prc.manager", "INV-AP", invId);
        assertThat(status("fin.ap_invoice", invId)).isEqualTo("APPROVED");
    }

    @Test
    void requisitionsCanBeRaisedFromMrpSuggestions() {
        List<Map<String, Object>> fg = jdbc.queryForList("SELECT id FROM sys.item WHERE code = 'FG-KP500'");
        Long plant = id("SELECT id FROM sys.plant WHERE code = 'P1'");
        java.time.LocalDate week = today().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        jdbc.update("INSERT INTO scm.mps (plant_id, item_id, week_start, qty) VALUES (?, ?, ?, 4000) ON CONFLICT (plant_id, item_id, week_start) DO UPDATE SET qty = 4000",
                plant, fg.getFirst().get("id"), java.sql.Date.valueOf(week));
        Long runId = scm.runMrp("scm.manager");
        List<Long> buy = jdbc.queryForList("""
                SELECT m.id FROM scm.mrp_line m JOIN sys.item i ON i.id = m.item_id WHERE m.run_id = ? AND m.action = 'BUY' AND i.code = 'PM-KPS-000'""",
                Long.class, runId);
        assertThat(buy).hasSize(1);
        BigDecimal planned = jdbc.queryForObject("SELECT planned_qty FROM scm.mrp_line WHERE id = ?", BigDecimal.class, buy.getFirst());
        assertThat(planned.remainder(new BigDecimal("50000"))).isEqualByComparingTo("0");
        Long prId = idOf(as("prc.staf", () -> requisitions.fromMrp(new PurchaseRequisitionController.FromMrp(buy, null))));
        assertThat(jdbc.queryForObject("SELECT source FROM prc.pr WHERE id = ?", String.class, prId)).isEqualTo("MRP");
        assertThat(jdbc.queryForObject("SELECT pr_line_id FROM scm.mrp_line WHERE id = ?", Long.class, buy.getFirst())).isNotNull();
        jdbc.update("DELETE FROM scm.mps WHERE plant_id = ? AND week_start = ?", plant, java.sql.Date.valueOf(week));
    }
}
