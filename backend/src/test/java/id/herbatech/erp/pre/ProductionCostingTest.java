package id.herbatech.erp.pre;

import id.herbatech.erp.IntegrationTest;
import id.herbatech.erp.scm.InventoryService;
import id.herbatech.erp.scm.Lot;
import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Produksi M2: WO → permintaan bahan dari BOM → serah bahan FEFO oleh gudang (WIP) → hasil produksi bernilai → tutup biaya batch
 * (FIN-53) sehingga saldo WIP batch nol.
 */
class ProductionCostingTest extends IntegrationTest {

    @Autowired WorkOrderController workOrders;
    @Autowired ProductionOutputController outputs;
    @Autowired MaterialRequestController requests;
    @Autowired InventoryService inventory;
    @Autowired DocumentWorkflowService workflow;
    @Autowired id.herbatech.erp.fin.FinTestApi fin;

    private static Long idOf(DocumentApi.Envelope e) {
        return ((Number) e.doc().get("id")).longValue();
    }

    private Long item(String code) {
        return id("SELECT id FROM sys.item WHERE code = ?", code);
    }

    private void releasedStock(String itemCode, String wh, String bin, String qty, String cost) {
        asVoid("scm.manager", () -> {
            Lot lot = inventory.createLot(item(itemCode), "TRM" + System.nanoTime() % 100000000, "S-1", today().minusDays(20), today().plusDays(500), "TEST");
            lot.setQcStatus(Lot.QcStatus.RELEASED);
            Long loc = id("SELECT l.id FROM sys.location l JOIN sys.warehouse w ON w.id = l.warehouse_id WHERE w.code = ? AND l.bin_code = ?", wh, bin);
            inventory.move(new InventoryService.MoveCommand(InventoryService.MoveType.RECEIPT, item(itemCode), lot.getId(), null, loc,
                    new BigDecimal(qty), new BigDecimal(cost), today(), "TEST", 0L, "TEST", "Stok uji"));
        });
    }

    @Test
    void materialsIssuedToBatchAreClearedWhenBatchCostIsClosed() {
        releasedStock("RM-SIM-001", "WH-RM", "A-01-01", "40", "65000");
        releasedStock("RM-EKS-001", "WH-RM", "A-01-02", "15", "450000");
        releasedStock("RM-EXC-001", "WH-RM", "A-01-01", "30", "28000");
        releasedStock("PM-KPS-000", "WH-PM", "B-01-01", "130000", "95");
        releasedStock("PM-DUS-060", "WH-PM", "B-01-01", "2500", "1200");

        WorkOrder w = new WorkOrder();
        w.setProductItemId(item("FG-KP500"));
        w.setLineId(id("SELECT id FROM pre.line WHERE code = 'LN-02'"));
        w.setQtyPlan(new BigDecimal("1000"));
        w.setPlannedStart(today());
        WorkOrder.Operator o = new WorkOrder.Operator();
        o.setEmployeeId(id("SELECT id FROM hc.employee WHERE nik = 'E0032'"));
        w.getOperators().add(o);
        Long woId = idOf(as("pre.spv", () -> workOrders.create(w)));
        asVoid("pre.spv", () -> workflow.submit("WO", woId));
        approveAs("pre.manager", "WO", woId);

        MaterialRequest mr = new MaterialRequest();
        mr.setWoId(woId);
        var created = as("pre.operator", () -> requests.create(mr));
        Long mrId = idOf(created);
        assertThat((List<?>) created.doc().get("lines")).hasSize(5);
        asVoid("pre.operator", () -> workflow.submit("MR", mrId));
        approveAs("pre.spv", "MR", mrId);

        List<MaterialRequestController.Pick> picks = as("scm.spv", () -> requests.picks(mrId));
        assertThat(picks).allMatch(p -> p.shortage() == null);
        as("scm.spv", () -> requests.issue(mrId, new MaterialRequestController.IssueRequest(picks.stream()
                .map(p -> new MaterialRequestController.IssueLine(p.lineId(), p.lotId(), p.locationId(), p.qty())).toList())));
        assertThat(status("pre.material_request", mrId)).isEqualTo("DONE");
        BigDecimal issued = jdbc.queryForObject("SELECT issued_value FROM pre.material_request WHERE id = ?", BigDecimal.class, mrId);
        assertThat(issued).isPositive();

        as("pre.spv", () -> workOrders.start(woId));
        ProductionOutput out = new ProductionOutput();
        out.setWoId(woId);
        out.setQtyGood(new BigDecimal("990"));
        out.setQtyReject(new BigDecimal("10"));
        out.setRejectReasonId(id("SELECT id FROM pre.reject_reason WHERE code = 'R01'"));
        Long outId = idOf(as("pre.operator", () -> outputs.create(out)));
        asVoid("pre.operator", () -> workflow.submit("HP", outId));
        approveAs("pre.spv", "HP", outId);
        asVoid("pre.manager", () -> workflow.post("HP", outId, null));
        assertThat(jdbc.queryForObject("SELECT total_cost FROM pre.production_output WHERE id = ?", BigDecimal.class, outId)).isPositive();

        Map<String, Object> closed = fin.closeBatch("fin.manager", woId);
        assertThat((Boolean) closed.get("closed")).isTrue();
        BigDecimal wip = jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.debit - l.credit), 0) FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id
                JOIN fin.account a ON a.id = l.account_id
                WHERE a.code = '1303' AND ((e.source_doc_type = 'MR' AND e.source_doc_id = ?) OR (e.source_doc_type = 'HP' AND e.source_doc_id = ?)
                   OR (e.source_doc_type = 'WO' AND e.source_doc_id = ?))""", BigDecimal.class, mrId, outId, woId);
        assertThat(wip).isEqualByComparingTo("0");
    }
}
