package id.herbatech.erp.fin;

import id.herbatech.erp.shared.document.DocumentWorkflowService;
import id.herbatech.erp.shared.error.BusinessException;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * API untuk modul lain membuat uang muka kerja (mis. SPD yang disetujui di HC). Draft dibuat sistem lalu diproses
 * kasir FIN (ajukan → setujui → posting = pencairan).
 */
@Service
public class CashAdvanceService {

    private final CashVoucherHandler handler;
    private final DocumentWorkflowService workflow;
    private final JdbcTemplate jdbc;

    CashAdvanceService(CashVoucherHandler handler, @Lazy DocumentWorkflowService workflow, JdbcTemplate jdbc) {
        this.handler = handler;
        this.workflow = workflow;
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String createAdvanceDraft(Long plantId, Long employeeId, BigDecimal amount, String description, String sourceDocNo) {
        Long cash = jdbc.queryForList("""
                        SELECT id FROM fin.bank_account WHERE active AND kind = 'KAS' AND (plant_id = ? OR plant_id IS NULL)
                        ORDER BY plant_id NULLS LAST, id LIMIT 1""", Long.class, plantId)
                .stream().findFirst().orElseThrow(() -> new BusinessException("KK_CASH", "Belum ada rekening kas di FIN-99"));
        CashVoucher v = new CashVoucher();
        v.setKind("ADVANCE");
        v.setCashAccountId(cash);
        v.setEmployeeId(employeeId);
        v.setAmount(amount);
        v.setDescription(description.length() > 255 ? description.substring(0, 255) : description);
        v.setSourceDocNo(sourceDocNo);
        return workflow.initSystemDraft(handler, v, plantId, "Dibuat otomatis dari " + sourceDocNo).getDocNo();
    }

    /** Uang muka yang belum dipertanggungjawabkan per karyawan (untuk ESS-09 & offboarding). */
    public BigDecimal outstanding(Long employeeId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM fin.cash_voucher
                WHERE employee_id = ? AND kind = 'ADVANCE' AND status = 'POSTED' AND settled_amount = 0""",
                BigDecimal.class, employeeId);
    }
}
