package id.herbatech.erp.pre;

import id.herbatech.erp.IntegrationTest;
import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Alur PRE M1: WO dengan operator berkualifikasi → rilis (nomor batch) → mulai → hasil produksi → lot karantina. */
class PreFlowTest extends IntegrationTest {

    @Autowired WorkOrderController workOrders;
    @Autowired ProductionOutputController outputs;
    @Autowired DocumentWorkflowService workflow;

    private static Long idOf(DocumentApi.Envelope e) {
        return ((Number) e.doc().get("id")).longValue();
    }

    private WorkOrder wo(String operatorNik) {
        WorkOrder w = new WorkOrder();
        w.setProductItemId(id("SELECT id FROM sys.item WHERE code = 'FG-KP500'"));
        w.setLineId(id("SELECT id FROM pre.line WHERE code = 'LN-02'"));
        w.setQtyPlan(new BigDecimal("1000"));
        w.setPlannedStart(today());
        WorkOrder.Operator o = new WorkOrder.Operator();
        o.setEmployeeId(id("SELECT id FROM hc.employee WHERE nik = ?", operatorNik));
        w.getOperators().add(o);
        return w;
    }

    @Test
    void unqualifiedOperatorBlocksWorkOrder() {
        Long id = idOf(as("pre.spv", () -> workOrders.create(wo("E0012"))));
        assertThatThrownBy(() -> asVoid("pre.spv", () -> workflow.submit("WO", id))).hasMessageContaining("berkualifikasi");
    }

    @Test
    void outputGoesToQuarantineAsNewBatchLot() {
        Long woId = idOf(as("pre.spv", () -> workOrders.create(wo("E0032"))));
        asVoid("pre.spv", () -> workflow.submit("WO", woId));
        approveAs("pre.manager", "WO", woId);
        String batch = jdbc.queryForObject("SELECT batch_no FROM pre.work_order WHERE id = ?", String.class, woId);
        assertThat(batch).matches("KP\\d{4}\\d{4}");
        as("pre.spv", () -> workOrders.start(woId));

        ProductionOutput low = new ProductionOutput();
        low.setWoId(woId);
        low.setQtyGood(new BigDecimal("900"));
        Long lowId = idOf(as("pre.operator", () -> outputs.create(low)));
        assertThatThrownBy(() -> asVoid("pre.operator", () -> workflow.submit("HP", lowId))).hasMessageContaining("di bawah minimum");
        asVoid("pre.operator", () -> workflow.cancel("HP", lowId, "Salah input"));

        ProductionOutput out = new ProductionOutput();
        out.setWoId(woId);
        out.setQtyGood(new BigDecimal("980"));
        out.setQtyReject(new BigDecimal("20"));
        out.setRejectReasonId(id("SELECT id FROM pre.reject_reason WHERE code = 'R01'"));
        Long outId = idOf(as("pre.operator", () -> outputs.create(out)));
        asVoid("pre.operator", () -> workflow.submit("HP", outId));
        approveAs("pre.spv", "HP", outId);
        asVoid("pre.manager", () -> workflow.post("HP", outId, null));

        Map<String, Object> lot = jdbc.queryForMap("""
                SELECT l.qc_status, q.qty, loc.is_quarantine FROM scm.lot l JOIN scm.stock_quant q ON q.lot_id = l.id
                JOIN sys.location loc ON loc.id = q.location_id WHERE l.lot_no = ?""", batch);
        assertThat(lot.get("qc_status")).isEqualTo("QUARANTINE");
        assertThat((BigDecimal) lot.get("qty")).isEqualByComparingTo("980");
        assertThat(lot.get("is_quarantine")).isEqualTo(true);
        assertThat(status("pre.work_order", woId)).isEqualTo("DONE");
    }
}
