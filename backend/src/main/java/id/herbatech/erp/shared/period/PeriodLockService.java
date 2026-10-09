package id.herbatech.erp.shared.period;

import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.error.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Aturan tanggal & periode (PRD §13): transaksi tidak boleh bertanggal di periode yang sudah dikunci FIN-70,
 * dan tidak boleh bertanggal maju lebih dari 1 hari.
 */
@Service
public class PeriodLockService {

    /** Modul yang punya kunci periode sendiri. Urutan closing PRD FIN aturan 4. */
    public static final List<String> MODULES = List.of("HC", "SCM", "PRE", "COST", "AP", "AR", "TAX", "FIN");

    public record PeriodLockView(Long id, String moduleCode, int year, int month, boolean locked, Long lockedBy,
                                 java.time.Instant lockedAt) {
    }

    private final JdbcTemplate jdbc;
    private final TimeService time;

    public PeriodLockService(JdbcTemplate jdbc, TimeService time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    public void assertDateAllowed(String module, LocalDate date) {
        if (date == null) {
            throw new BusinessException("DATE", "Tanggal dokumen wajib diisi");
        }
        if (date.isAfter(time.today().plusDays(1))) {
            throw new BusinessException("DATE_FUTURE", "Tanggal dokumen tidak boleh lebih dari 1 hari ke depan");
        }
        if (isLocked(module, date)) {
            throw BusinessException.of("PERIOD_LOCKED", "Periode %02d/%d sudah dikunci untuk modul %s. Transaksi bertanggal periode ini ditolak.",
                    date.getMonthValue(), date.getYear(), module);
        }
    }

    public boolean isLocked(String module, LocalDate date) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM core.period_lock
                WHERE locked AND year = ? AND month = ? AND (module_code = ? OR module_code = 'ALL')""",
                Integer.class, date.getYear(), date.getMonthValue(), module);
        return n != null && n > 0;
    }

    public List<PeriodLockView> list(int year) {
        return jdbc.query("SELECT * FROM core.period_lock WHERE year = ? ORDER BY month, module_code",
                (rs, i) -> new PeriodLockView(rs.getLong("id"), rs.getString("module_code"), rs.getInt("year"),
                        rs.getInt("month"), rs.getBoolean("locked"), (Long) rs.getObject("locked_by"),
                        rs.getTimestamp("locked_at").toInstant()), year);
    }

    @Transactional
    public void setLocked(String module, int year, int month, boolean locked, Long userId) {
        if (!"ALL".equals(module) && !MODULES.contains(module)) {
            throw new BusinessException("MODULE", "Modul tidak dikenal: " + module);
        }
        jdbc.update("""
                INSERT INTO core.period_lock (module_code, year, month, locked, locked_by, locked_at)
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (module_code, year, month) DO UPDATE
                SET locked = EXCLUDED.locked, locked_by = EXCLUDED.locked_by, locked_at = now()""",
                module, year, month, locked, userId);
    }
}
