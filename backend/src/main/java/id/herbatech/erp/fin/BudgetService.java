package id.herbatech.erp.fin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * FIN-51 Kontrol anggaran: sisa = anggaran berlaku s.d. bulan transaksi − realisasi (jurnal terposting)
 * − komitmen terbuka (PR/PO). Mode OFF / WARN / BLOCK diatur di FIN-99 (BUDGET_CONTROL).
 */
@Service
public class BudgetService {

    public record Check(Long costCenterId, Long accountId, BigDecimal budget, BigDecimal actual, BigDecimal committed,
                        BigDecimal requested, BigDecimal available, boolean exceeded, boolean budgeted) {
    }

    private final JdbcTemplate jdbc;
    private final FinSupport fin;

    BudgetService(JdbcTemplate jdbc, FinSupport fin) {
        this.jdbc = jdbc;
        this.fin = fin;
    }

    public String mode() {
        return fin.param("BUDGET_CONTROL", "WARN");
    }

    /** Cek satu kombinasi cost center × akun beban, kumulatif dari awal tahun s.d. bulan tanggal transaksi. */
    public Check check(Long plantId, Long costCenterId, Long accountId, LocalDate date, BigDecimal requested) {
        int month = date.getMonthValue();
        StringBuilder sum = new StringBuilder("0");
        for (int m = 1; m <= month; m++) {
            sum.append(" + bl.m").append(String.format("%02d", m));
        }
        BigDecimal budget = jdbc.queryForObject("""
                SELECT COALESCE(SUM(%s), 0) FROM fin.budget_line bl JOIN fin.budget b ON b.id = bl.budget_id
                WHERE b.status = 'POSTED' AND b.plant_id = ? AND b.year = ? AND bl.cost_center_id = ? AND bl.account_id = ?"""
                .formatted(sum), BigDecimal.class, plantId, date.getYear(), costCenterId, accountId);
        Long budgetRows = jdbc.queryForObject("""
                SELECT count(*) FROM fin.budget_line bl JOIN fin.budget b ON b.id = bl.budget_id
                WHERE b.status = 'POSTED' AND b.plant_id = ? AND b.year = ? AND bl.cost_center_id = ? AND bl.account_id = ?""",
                Long.class, plantId, date.getYear(), costCenterId, accountId);
        BigDecimal actual = jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.debit - l.credit), 0) FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id
                WHERE e.posted_at IS NOT NULL AND e.plant_id = ? AND l.cost_center_id = ? AND l.account_id = ?
                  AND e.doc_date BETWEEN ? AND ?""", BigDecimal.class, plantId, costCenterId, accountId,
                Date.valueOf(LocalDate.of(date.getYear(), 1, 1)), Date.valueOf(YearMonth.from(date).atEndOfMonth()));
        BigDecimal committed = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM fin.budget_commitment
                WHERE state = 'OPEN' AND plant_id = ? AND cost_center_id = ? AND account_id = ? AND period BETWEEN ? AND ?""",
                BigDecimal.class, plantId, costCenterId, accountId, date.getYear() + "01", "%d%02d".formatted(date.getYear(), month));
        BigDecimal available = budget.subtract(actual).subtract(committed);
        boolean budgeted = budgetRows != null && budgetRows > 0;
        return new Check(costCenterId, accountId, budget, actual, committed, requested, available,
                budgeted && requested.compareTo(available) > 0, budgeted);
    }

    /** Hanya akun beban yang dikontrol anggaran. */
    public boolean isControlled(Long accountId) {
        return "EXPENSE".equals(jdbc.queryForList("SELECT type FROM fin.account WHERE id = ?", String.class, accountId)
                .stream().findFirst().orElse(null));
    }

    /** Ringkasan realisasi vs anggaran per cost center × akun untuk tahun berjalan s.d. bulan tertentu (FIN-51). */
    public List<Map<String, Object>> realization(Long plantId, int year, int uptoMonth) {
        StringBuilder sum = new StringBuilder("0");
        for (int m = 1; m <= uptoMonth; m++) {
            sum.append(" + bl.m").append(String.format("%02d", m));
        }
        return jdbc.queryForList("""
                WITH bud AS (
                    SELECT bl.cost_center_id, bl.account_id, SUM(%s) AS budget, SUM(bl.total) AS budget_year
                    FROM fin.budget_line bl JOIN fin.budget b ON b.id = bl.budget_id
                    WHERE b.status = 'POSTED' AND b.plant_id = ? AND b.year = ?
                    GROUP BY bl.cost_center_id, bl.account_id),
                act AS (
                    SELECT l.cost_center_id, l.account_id, SUM(l.debit - l.credit) AS actual
                    FROM fin.journal_line l JOIN fin.journal_entry e ON e.id = l.entry_id JOIN fin.account a ON a.id = l.account_id
                    WHERE e.posted_at IS NOT NULL AND e.plant_id = ? AND a.type = 'EXPENSE' AND l.cost_center_id IS NOT NULL
                      AND e.doc_date BETWEEN ? AND ?
                    GROUP BY l.cost_center_id, l.account_id),
                com AS (
                    SELECT cost_center_id, account_id, SUM(amount) AS committed FROM fin.budget_commitment
                    WHERE state = 'OPEN' AND plant_id = ? AND period LIKE ?
                    GROUP BY cost_center_id, account_id)
                SELECT COALESCE(bud.cost_center_id, act.cost_center_id) AS cost_center_id,
                       COALESCE(bud.account_id, act.account_id) AS account_id,
                       cc.code AS cc_code, cc.name AS cc_name, d.name AS department, a.code AS account_code, a.name AS account_name,
                       COALESCE(bud.budget, 0) AS budget, COALESCE(bud.budget_year, 0) AS budget_year,
                       COALESCE(act.actual, 0) AS actual, COALESCE(com.committed, 0) AS committed
                FROM bud FULL OUTER JOIN act ON act.cost_center_id = bud.cost_center_id AND act.account_id = bud.account_id
                LEFT JOIN com ON com.cost_center_id = COALESCE(bud.cost_center_id, act.cost_center_id)
                             AND com.account_id = COALESCE(bud.account_id, act.account_id)
                JOIN sys.cost_center cc ON cc.id = COALESCE(bud.cost_center_id, act.cost_center_id)
                JOIN sys.department d ON d.id = cc.department_id
                JOIN fin.account a ON a.id = COALESCE(bud.account_id, act.account_id)
                ORDER BY cc.code, a.code""".formatted(sum),
                plantId, year, plantId, Date.valueOf(LocalDate.of(year, 1, 1)),
                Date.valueOf(YearMonth.of(year, uptoMonth).atEndOfMonth()), plantId, year + "%");
    }
}
