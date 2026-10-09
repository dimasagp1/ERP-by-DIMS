package id.herbatech.erp.fin;

import id.herbatech.erp.IntegrationTest;
import id.herbatech.erp.shared.approval.ApprovalService;
import id.herbatech.erp.shared.document.DocumentWorkflowService;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.period.PeriodLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Siklus dokumen PRD §13 lewat Jurnal Umum: penomoran, approval berjenjang sesuai nilai,
 * pemisahan tugas, posting, reversal, kunci periode, dan audit trail.
 */
class JournalWorkflowTest extends IntegrationTest {

    @Autowired
    JournalController journals;
    @Autowired
    DocumentWorkflowService workflow;
    @Autowired
    ApprovalService approvals;
    @Autowired
    PeriodLockService periods;

    Long expense;
    Long bank;
    Long costCenter;
    LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Jakarta"));

    @BeforeEach
    void ids() {
        expense = id("SELECT id FROM fin.account WHERE code = '6201'");
        bank = id("SELECT id FROM fin.account WHERE code = '1111'");
        costCenter = id("SELECT id FROM sys.cost_center WHERE code = '2110'");
    }

    private JournalController.JournalRequest request(String amount) {
        BigDecimal v = new BigDecimal(amount);
        return new JournalController.JournalRequest(today, "Uji jurnal " + amount, List.of(
                new JournalController.LineRequest(expense, costCenter, null, v, BigDecimal.ZERO),
                new JournalController.LineRequest(bank, null, null, BigDecimal.ZERO, v)), null);
    }

    private Long pendingTaskFor(String user, Long docId) {
        return as(user, () -> approvals.inbox(null).stream()
                .filter(t -> t.docType().equals("JV") && t.docId().equals(docId))
                .findFirst().orElseThrow().id());
    }

    @Test
    void largeJournalNeedsTwoLevelsThenPostsAndReverses() {
        var draft = as("fin.staf", () -> journals.create(request("150000000")));
        assertThat(draft.docNo()).matches("JV/P1/\\d{4}/\\d{5}");
        assertThat(draft.status()).isEqualTo("DRAFT");

        asVoid("fin.staf", () -> workflow.submit("JV", draft.id()));

        Long l1 = pendingTaskFor("fin.spv", draft.id());
        assertThatThrownBy(() -> asVoid("fin.staf", () -> approvals.approve(l1, null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Pemisahan tugas");
        asVoid("fin.spv", () -> approvals.approve(l1, "OK", null));

        Long l2 = pendingTaskFor("fin.manager", draft.id());
        asVoid("fin.manager", () -> approvals.approve(l2, null, null));
        assertThat(as("fin.manager", () -> journals.get(draft.id())).status()).isEqualTo("APPROVED");

        asVoid("fin.manager", () -> workflow.post("JV", draft.id(), null));
        assertThat(as("fin.manager", () -> journals.get(draft.id())).actions()).containsExactly("REVERSE");

        var reversal = as("fin.manager", () -> workflow.reverse("JV", draft.id(), "Salah akun", null));
        var original = as("fin.manager", () -> journals.get(draft.id()));
        assertThat(original.status()).isEqualTo("CANCELLED");
        assertThat(original.reversedById()).isEqualTo(reversal.getId());

        BigDecimal net = jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.debit - l.credit), 0) FROM fin.journal_line l
                JOIN fin.journal_entry e ON e.id = l.entry_id WHERE e.id IN (?, ?) AND l.account_id = ?""",
                BigDecimal.class, draft.id(), reversal.getId(), expense);
        assertThat(net).isEqualByComparingTo("0");

        List<Map<String, Object>> audit = jdbc.queryForList("""
                SELECT reason FROM core.audit_log WHERE table_name = 'fin.journal_entry' AND record_id = ?
                  AND field = 'status' AND new_value = 'CANCELLED'""", String.valueOf(draft.id()));
        assertThat(audit).hasSize(1);
        assertThat(audit.getFirst().get("reason")).isEqualTo("Salah akun");
    }

    @Test
    void smallJournalNeedsOnlyDirectSuperior() {
        var draft = as("fin.staf", () -> journals.create(request("2500000")));
        asVoid("fin.staf", () -> workflow.submit("JV", draft.id()));
        asVoid("fin.spv", () -> approvals.approve(pendingTaskFor("fin.spv", draft.id()), null, null));
        assertThat(as("fin.staf", () -> journals.get(draft.id())).status()).isEqualTo("APPROVED");
    }

    @Test
    void rejectedJournalBecomesEditableAgain() {
        var draft = as("fin.staf", () -> journals.create(request("1000000")));
        asVoid("fin.staf", () -> workflow.submit("JV", draft.id()));
        Long task = pendingTaskFor("fin.spv", draft.id());
        assertThatThrownBy(() -> asVoid("fin.spv", () -> approvals.reject(task, " ", null)))
                .hasMessageContaining("Alasan");
        asVoid("fin.spv", () -> approvals.reject(task, "Lampiran tagihan belum ada", null));
        var rejected = as("fin.staf", () -> journals.get(draft.id()));
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.actions()).contains("EDIT", "SUBMIT");
    }

    @Test
    void unbalancedJournalCannotBeSubmitted() {
        var bad = new JournalController.JournalRequest(today, "Tidak seimbang", List.of(
                new JournalController.LineRequest(expense, costCenter, null, new BigDecimal("100"), BigDecimal.ZERO),
                new JournalController.LineRequest(bank, null, null, BigDecimal.ZERO, new BigDecimal("90"))), null);
        var draft = as("fin.staf", () -> journals.create(bad));
        assertThatThrownBy(() -> asVoid("fin.staf", () -> workflow.submit("JV", draft.id())))
                .hasMessageContaining("tidak seimbang");
    }

    @Test
    void costCenterRequiredForExpenseAccount() {
        var noCc = new JournalController.JournalRequest(today, "Tanpa CC", List.of(
                new JournalController.LineRequest(expense, null, null, new BigDecimal("100"), BigDecimal.ZERO),
                new JournalController.LineRequest(bank, null, null, BigDecimal.ZERO, new BigDecimal("100"))), null);
        var draft = as("fin.staf", () -> journals.create(noCc));
        assertThatThrownBy(() -> asVoid("fin.staf", () -> workflow.submit("JV", draft.id())))
                .hasMessageContaining("wajib cost center");
    }

    @Test
    void lockedPeriodAndFutureDatesAreRejected() {
        LocalDate lastMonth = today.minusMonths(1);
        asVoid("fin.manager", () -> periods.setLocked("FIN", lastMonth.getYear(), lastMonth.getMonthValue(), true, null));
        var old = new JournalController.JournalRequest(lastMonth, "Periode terkunci", request("1").lines(), null);
        assertThatThrownBy(() -> as("fin.staf", () -> journals.create(old))).hasMessageContaining("sudah dikunci");

        var future = new JournalController.JournalRequest(today.plusDays(3), "Maju", request("1").lines(), null);
        assertThatThrownBy(() -> as("fin.staf", () -> journals.create(future))).hasMessageContaining("1 hari ke depan");
        asVoid("fin.manager", () -> periods.setLocked("FIN", lastMonth.getYear(), lastMonth.getMonthValue(), false, null));
    }

    @Test
    void auditorCannotCreateDocuments() {
        assertThatThrownBy(() -> as("auditor", () -> journals.create(request("1"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void auditLogIsAppendOnly() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM core.audit_log"))
                .hasMessageContaining("append-only");
    }
}
