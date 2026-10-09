package id.herbatech.erp.scm;

import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentWorkflowService;
import id.herbatech.erp.shared.security.CurrentUser;
import id.herbatech.erp.shared.security.ErpAuthentication;
import id.herbatech.erp.shared.security.UserGrantLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.function.Supplier;

/** Bantuan test lintas paket: dokumen SCM (controller package-private) dijalankan sebagai pengguna demo. */
@Component
public class ScmTestApi {

    private final GoodsReceiptController receipts;
    private final DocumentWorkflowService workflow;
    private final MrpService mrp;
    private final UserGrantLoader grants;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    ScmTestApi(GoodsReceiptController receipts, DocumentWorkflowService workflow, MrpService mrp, UserGrantLoader grants, JdbcTemplate jdbc,
               TransactionTemplate tx) {
        this.receipts = receipts;
        this.workflow = workflow;
        this.mrp = mrp;
        this.grants = grants;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    <T> T as(String user, Supplier<T> action) {
        Long id = jdbc.queryForObject("SELECT id FROM sys.app_user WHERE username = ?", Long.class, user);
        CurrentUser u = grants.load(id);
        SecurityContextHolder.getContext().setAuthentication(new ErpAuthentication(u, null));
        return tx.execute(s -> action.get());
    }

    /** GR satu baris PO (lot supplier & kedaluwarsa contoh) lalu diajukan → otomatis disetujui & diposting. */
    public Long receive(String user, Long poId, Long poLineId, String qty, String supplierLot) {
        GoodsReceipt gr = new GoodsReceipt();
        gr.setPoId(poId);
        gr.setDeliveryNoteNo("SJ-" + supplierLot);
        GoodsReceipt.Line l = new GoodsReceipt.Line();
        l.setPoLineId(poLineId);
        l.setQty(new BigDecimal(qty));
        l.setSupplierLot(supplierLot);
        l.setMfgDate(java.time.LocalDate.now().minusDays(30));
        l.setExpDate(java.time.LocalDate.now().plusYears(2));
        gr.getLines().add(l);
        DocumentApi.Envelope e = as(user, () -> receipts.create(gr));
        Long id = ((Number) e.doc().get("id")).longValue();
        as(user, () -> {
            workflow.submit("GR", id);
            return null;
        });
        return id;
    }

    public Long runMrp(String user) {
        return as(user, () -> mrp.run(jdbc.queryForObject("SELECT id FROM sys.plant WHERE code = 'P1'", Long.class), "MANUAL", null));
    }
}
