package id.herbatech.erp.fin;

import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.security.CurrentUser;
import id.herbatech.erp.shared.security.ErpAuthentication;
import id.herbatech.erp.shared.security.UserGrantLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

/** Bantuan test lintas paket untuk dokumen FIN (controller package-private). */
@Component
public class FinTestApi {

    private final ApInvoiceController apInvoices;
    private final CostingController costing;
    private final UserGrantLoader grants;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    FinTestApi(ApInvoiceController apInvoices, CostingController costing, UserGrantLoader grants, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.apInvoices = apInvoices;
        this.costing = costing;
        this.grants = grants;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    private void login(String user) {
        Long uid = jdbc.queryForObject("SELECT id FROM sys.app_user WHERE username = ?", Long.class, user);
        CurrentUser u = grants.load(uid);
        SecurityContextHolder.getContext().setAuthentication(new ErpAuthentication(u, null));
    }

    /** Tutup biaya batch (FIN-53) dan kembalikan ringkasannya. */
    public java.util.Map<String, Object> closeBatch(String user, Long woId) {
        login(user);
        CostingService.BatchCost b = tx.execute(s -> costing.close(woId));
        return java.util.Map.of("closed", b.closed(), "variance", b.variance(), "total", b.total());
    }

    /** Hitung biaya standar satu produk (FIN-52). */
    public BigDecimal standardCost(String user, Long itemId) {
        login(user);
        return (BigDecimal) tx.execute(s -> costing.calculate(new CostingController.CalcRequest(itemId))).getFirst().get("total");
    }

    /** Draft faktur supplier berbasis PO, satu baris (qty × harga faktur). */
    public Long invoice(String user, Long poId, Long poLineId, String qty, String price) {
        login(user);
        ApInvoice inv = new ApInvoice();
        inv.setPoId(poId);
        inv.setSupplierInvoiceNo("SI-" + System.nanoTime());
        inv.setTaxInvoiceNo("010.000-26." + (System.nanoTime() % 100000000));
        ApInvoice.Line l = new ApInvoice.Line();
        l.setPoLineId(poLineId);
        l.setQty(new BigDecimal(qty));
        l.setUnitPrice(new BigDecimal(price));
        inv.getLines().add(l);
        DocumentApi.Envelope e = tx.execute(s -> apInvoices.create(inv));
        return ((Number) e.doc().get("id")).longValue();
    }
}
