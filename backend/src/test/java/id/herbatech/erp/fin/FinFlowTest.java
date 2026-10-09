package id.herbatech.erp.fin;

import id.herbatech.erp.IntegrationTest;
import id.herbatech.erp.shared.document.DocumentWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Alur FIN M1: hutang → pembayaran, piutang tertahan kredit, anggaran → approval Direktur, penyusutan. */
class FinFlowTest extends IntegrationTest {

    @Autowired ApInvoiceController apInvoices;
    @Autowired PaymentController payments;
    @Autowired ArInvoiceController arInvoices;
    @Autowired BudgetController budgets;
    @Autowired CashVoucherController vouchers;
    @Autowired FixedAssetController assets;
    @Autowired DepreciationController depreciations;
    @Autowired DocumentWorkflowService workflow;

    private Long account(String code) {
        return id("SELECT id FROM fin.account WHERE code = ?", code);
    }

    private Long cc(String code) {
        return id("SELECT id FROM sys.cost_center WHERE code = ?", code);
    }

    private Long partner(String code) {
        return id("SELECT id FROM sys.partner WHERE code = ?", code);
    }

    private static Long idOf(id.herbatech.erp.shared.document.DocumentApi.Envelope e) {
        return ((Number) e.doc().get("id")).longValue();
    }

    @Test
    void supplierInvoiceWithTaxesIsPaidThroughApprovedPayment() {
        ApInvoice inv = new ApInvoice();
        inv.setPartnerId(partner("SUP-0001"));
        inv.setSupplierInvoiceNo("INV-T-" + System.nanoTime());
        inv.setTaxInvoiceNo("010.000-26.00000001");
        inv.setPphTaxCode("PPH23-JASA");
        ApInvoice.Line l = new ApInvoice.Line();
        l.setAccountId(account("6204"));
        l.setCostCenterId(cc("7100"));
        l.setQty(BigDecimal.ONE);
        l.setUnitPrice(new BigDecimal("10000000"));
        inv.getLines().add(l);
        var created = as("fin.staf", () -> apInvoices.create(inv));
        Long invId = idOf(created);
        // PPN = 10 jt × 11/12 × 12% = 1,1 jt; PPh 23 = 2% × 10 jt
        assertThat(new BigDecimal(created.doc().get("ppnAmount").toString())).isEqualByComparingTo("1100000");
        assertThat(new BigDecimal(created.doc().get("pphAmount").toString())).isEqualByComparingTo("200000");
        assertThat(new BigDecimal(created.doc().get("payable").toString())).isEqualByComparingTo("10900000");

        asVoid("fin.staf", () -> workflow.submit("INV-AP", invId));
        approveAs("fin.manager", "INV-AP", invId);
        asVoid("fin.manager", () -> workflow.post("INV-AP", invId, null));

        Payment pay = new Payment();
        pay.setPartnerId(partner("SUP-0001"));
        pay.setBankAccountId(id("SELECT id FROM fin.bank_account WHERE code = 'BANK-OP'"));
        Payment.Allocation a = new Payment.Allocation();
        a.setInvoiceId(invId);
        a.setAmount(new BigDecimal("10900000"));
        pay.getAllocations().add(a);
        Long payId = idOf(as("fin.staf", () -> payments.create(pay)));
        asVoid("fin.staf", () -> workflow.submit("PAY", payId));
        approveAs("fin.manager", "PAY", payId);
        asVoid("fin.manager", () -> workflow.post("PAY", payId, null));

        BigDecimal outstanding = jdbc.queryForObject(
                "SELECT payable - advance_applied - paid_amount FROM fin.ap_invoice WHERE id = ?", BigDecimal.class, invId);
        assertThat(outstanding).isEqualByComparingTo("0");
        BigDecimal apBalance = jdbc.queryForObject("""
                SELECT SUM(l.credit - l.debit) FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id
                WHERE l.account_id = ? AND ((e.source_doc_type = 'INV-AP' AND e.source_doc_id = ?) OR (e.source_doc_type = 'PAY' AND e.source_doc_id = ?))""",
                BigDecimal.class, account("2101"), invId, payId);
        assertThat(apBalance).isEqualByComparingTo("0");
    }

    @Test
    void salesInvoiceOverCreditLimitIsHeldForFinanceApproval() {
        ArInvoice small = arInvoice("1000000");
        Long smallId = idOf(as("fin.staf", () -> arInvoices.create(small)));
        asVoid("fin.staf", () -> workflow.submit("INV-AR", smallId));
        assertThat(status("fin.ar_invoice", smallId)).isEqualTo("APPROVED");

        ArInvoice big = arInvoice("3000000000");
        Long bigId = idOf(as("fin.staf", () -> arInvoices.create(big)));
        asVoid("fin.staf", () -> workflow.submit("INV-AR", bigId));
        assertThat(status("fin.ar_invoice", bigId)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT credit_hold FROM fin.ar_invoice WHERE id = ?", Boolean.class, bigId)).isTrue();
        approveAs("fin.manager", "INV-AR", bigId);
        assertThat(status("fin.ar_invoice", bigId)).isEqualTo("APPROVED");
    }

    private ArInvoice arInvoice(String price) {
        ArInvoice inv = new ArInvoice();
        inv.setPartnerId(partner("CUS-0001"));
        inv.setWithPpn(false);
        ArInvoice.Line l = new ArInvoice.Line();
        l.setItemId(id("SELECT id FROM sys.item WHERE code = 'FG-KP500'"));
        l.setQty(BigDecimal.ONE);
        l.setUnitPrice(new BigDecimal(price));
        inv.getLines().add(l);
        return inv;
    }

    @Test
    void expenseOverBudgetNeedsDirectorApproval() {
        Budget b = new Budget();
        b.setYear((short) today().getYear());
        b.setKind("OPEX");
        b.setDescription("Uji anggaran ATK");
        Budget.Line bl = new Budget.Line();
        bl.setCostCenterId(cc("4100"));
        bl.setAccountId(account("6301"));
        for (String m : List.of("m01", "m02", "m03", "m04", "m05", "m06", "m07", "m08", "m09", "m10", "m11", "m12")) {
            new org.springframework.beans.BeanWrapperImpl(bl).setPropertyValue(m, new BigDecimal("1000000"));
        }
        b.getLines().add(bl);
        Long bid = idOf(as("fin.staf", () -> budgets.create(b)));
        asVoid("fin.staf", () -> workflow.submit("BGT", bid));
        approveAs("fin.manager", "BGT", bid);
        approveAs("direktur", "BGT", bid);
        asVoid("fin.manager", () -> workflow.post("BGT", bid, null));

        CashVoucher v = new CashVoucher();
        v.setKind("EXPENSE");
        v.setCashAccountId(id("SELECT id FROM fin.bank_account WHERE code = 'KAS-P1'"));
        v.setDescription("Pembelian ATK besar");
        CashVoucher.Line vl = new CashVoucher.Line();
        vl.setAccountId(account("6301"));
        vl.setCostCenterId(cc("4100"));
        vl.setAmount(new BigDecimal("20000000"));
        v.getLines().add(vl);
        Long vid = idOf(as("fin.staf", () -> vouchers.create(v)));
        asVoid("fin.staf", () -> workflow.submit("KK", vid));
        List<String> labels = jdbc.queryForList("SELECT assignee_label FROM core.approval_task WHERE doc_type = 'KK' AND doc_id = ? ORDER BY level",
                String.class, vid);
        assertThat(labels).anyMatch(s -> s.contains("melebihi anggaran"));
    }

    @Test
    void assetDepreciatesMonthlyCommercialAndFiscal() {
        FixedAsset a = new FixedAsset();
        a.setName("Laptop QC");
        a.setCategoryId(id("SELECT id FROM fin.asset_category WHERE code = 'IT'"));
        a.setAcquisitionCost(new BigDecimal("48000000"));
        a.setDepreciationStart(today().withDayOfMonth(1));
        a.setCostCenterId(cc("7200"));
        Long aid = idOf(as("fin.staf", () -> assets.create(a)));
        asVoid("fin.staf", () -> workflow.submit("AST", aid));
        approveAs("fin.manager", "AST", aid);
        asVoid("fin.manager", () -> workflow.post("AST", aid, null));

        DepreciationRun run = new DepreciationRun();
        run.setPeriod(today().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")));
        Long rid = idOf(as("fin.manager", () -> depreciations.create(run)));
        as("fin.manager", () -> depreciations.calculate(rid));
        BigDecimal line = jdbc.queryForObject("SELECT amount FROM fin.depreciation_line WHERE run_id = ? AND asset_id = ?", BigDecimal.class, rid, aid);
        BigDecimal fiscal = jdbc.queryForObject("SELECT fiscal_amount FROM fin.depreciation_line WHERE run_id = ? AND asset_id = ?", BigDecimal.class, rid, aid);
        assertThat(line).isEqualByComparingTo("1000000");   // 48 jt / 48 bulan
        assertThat(fiscal).isEqualByComparingTo("1000000"); // Kelompok 1: 4 tahun
        asVoid("fin.manager", () -> workflow.submit("DEP", rid));
        asVoid("fin.manager", () -> workflow.post("DEP", rid, null));
        assertThat(jdbc.queryForObject("SELECT accumulated FROM fin.fixed_asset WHERE id = ?", BigDecimal.class, aid)).isEqualByComparingTo("1000000");
    }
}
