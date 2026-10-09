package id.herbatech.erp.hc;

import id.herbatech.erp.IntegrationTest;
import id.herbatech.erp.shared.document.DocumentWorkflowService;
import id.herbatech.erp.shared.period.PeriodLockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Alur HC M1: layanan mandiri cuti & lembur, payroll dari absensi terkunci sampai jurnal. */
class HcFlowTest extends IntegrationTest {

    @Autowired LeaveController leaves;
    @Autowired OvertimeController overtimes;
    @Autowired PayrollController payrolls;
    @Autowired DocumentWorkflowService workflow;
    @Autowired PeriodLockService periods;

    private Long leaveType(String code) {
        return id("SELECT id FROM hc.leave_type WHERE code = ?", code);
    }

    @Test
    void operatorRequestsLeaveThroughEssAndSuperiorApproves() {
        LocalDate monday = today().plusWeeks(2).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LeaveRequest body = new LeaveRequest();
        body.setLeaveTypeId(leaveType("CT"));
        body.setStartDate(monday);
        body.setEndDate(monday.plusDays(2));
        body.setReason("Keperluan keluarga");
        // pre.operator tidak punya hak di aplikasi HC: dokumen dibuat sebagai layanan mandiri (ESS-01)
        var created = as("pre.operator", () -> leaves.create(body));
        Long id = ((Number) created.doc().get("id")).longValue();
        assertThat(((Number) created.doc().get("days")).doubleValue()).isEqualTo(3.0);
        asVoid("pre.operator", () -> workflow.submit("LV", id));

        approveAs("pre.spv", "LV", id);
        assertThat(status("hc.leave_request", id)).isEqualTo("APPROVED");
        Long emp = id("SELECT id FROM hc.employee WHERE nik = 'E0032'");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hc.attendance WHERE employee_id = ? AND status = 'CUTI' AND source_doc = ?",
                Integer.class, emp, created.meta().docNo())).isEqualTo(3);

        var balance = as("pre.operator", () -> leaves.balance(null, monday.getYear()));
        assertThat(balance.used()).isGreaterThanOrEqualTo(new BigDecimal("3"));
    }

    @Test
    void sickLeaveNeedsAttachment() {
        LeaveRequest body = new LeaveRequest();
        body.setLeaveTypeId(leaveType("SKT"));
        body.setStartDate(today().plusDays(30).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        body.setEndDate(body.getStartDate());
        Long id = ((Number) as("ga.staf", () -> leaves.create(body)).doc().get("id")).longValue();
        assertThatThrownBy(() -> asVoid("ga.staf", () -> workflow.submit("LV", id))).hasMessageContaining("wajib melampirkan");
    }

    @Test
    void selfServiceOvertimeIsForSelfAndLimitedToFourHours() {
        OvertimeRequest body = new OvertimeRequest();
        body.setWorkDate(today());
        body.setReason("Menyelesaikan batch");
        OvertimeRequest.Line l = new OvertimeRequest.Line();
        l.setEmployeeId(id("SELECT id FROM hc.employee WHERE nik = 'E0001'")); // diabaikan: diganti karyawan sendiri
        l.setStartTime(LocalTime.of(17, 0));
        l.setEndTime(LocalTime.of(22, 0));
        body.getLines().add(l);
        var created = as("pre.operator", () -> overtimes.create(body));
        @SuppressWarnings("unchecked")
        Map<String, Object> line = ((List<Map<String, Object>>) created.doc().get("lines")).getFirst();
        assertThat(((Number) line.get("employeeId")).longValue()).isEqualTo(id("SELECT id FROM hc.employee WHERE nik = 'E0032'"));
        Long id = ((Number) created.doc().get("id")).longValue();
        assertThatThrownBy(() -> asVoid("pre.operator", () -> workflow.submit("OT", id))).hasMessageContaining("maksimal 4 jam");
    }

    @Test
    void payrollRequiresLockedAttendanceAndPostsBalancedJournal() {
        YearMonth prev = YearMonth.from(today()).minusMonths(1);
        String period = prev.format(DateTimeFormatter.ofPattern("yyyyMM"));
        PayrollRun body = new PayrollRun();
        body.setPeriod(period);
        Long id = ((Number) as("hc.payroll", () -> payrolls.create(body)).doc().get("id")).longValue();

        assertThatThrownBy(() -> as("hc.payroll", () -> payrolls.calculate(id))).hasMessageContaining("Kunci absensi");
        asVoid("hc.manager", () -> periods.setLocked("HC", prev.getYear(), prev.getMonthValue(), true, null));
        try {
            var calc = as("hc.payroll", () -> payrolls.calculate(id));
            assertThat(calc.doc().get("warnings")).isNull();
            assertThat(((Number) calc.doc().get("employeeCount")).intValue()).isGreaterThanOrEqualTo(20);

            // HC manager tidak memegang peran Payroll: tidak boleh melihat slip
            assertThatThrownBy(() -> as("hc.manager", () -> payrolls.slips(id))).hasMessageContaining("payroll");

            asVoid("hc.payroll", () -> workflow.submit("PAYR", id));
            approveAs("hc.manager", "PAYR", id);
            approveAs("direktur", "PAYR", id);
            asVoid("hc.payroll", () -> workflow.post("PAYR", id, null));
            assertThat(status("hc.payroll_run", id)).isEqualTo("POSTED");

            Map<String, Object> je = jdbc.queryForMap("""
                    SELECT SUM(l.debit) AS d, SUM(l.credit) AS c,
                           SUM(CASE WHEN a.code = '2103' THEN l.credit ELSE 0 END) AS net
                    FROM fin.journal_entry e JOIN fin.journal_line l ON l.entry_id = e.id JOIN fin.account a ON a.id = l.account_id
                    WHERE e.source_doc_type = 'PAYR' AND e.source_doc_id = ?""", id);
            assertThat((BigDecimal) je.get("d")).isEqualByComparingTo((BigDecimal) je.get("c"));
            BigDecimal totalNet = jdbc.queryForObject("SELECT total_net FROM hc.payroll_run WHERE id = ?", BigDecimal.class, id);
            assertThat((BigDecimal) je.get("net")).isEqualByComparingTo(totalNet);
        } finally {
            asVoid("hc.manager", () -> periods.setLocked("HC", prev.getYear(), prev.getMonthValue(), false, null));
        }
    }
}
