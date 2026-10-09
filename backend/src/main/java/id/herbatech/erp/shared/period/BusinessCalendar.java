package id.herbatech.erp.shared.period;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Hitung hari kerja (tanpa Sabtu, Minggu, dan hari libur SYS-10) untuk SLA dan eskalasi approval. */
@Component
public class BusinessCalendar {

    private final JdbcTemplate jdbc;

    public BusinessCalendar(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Jumlah hari kerja penuh setelah {@code from} sampai dengan {@code to}. */
    public int workingDaysBetween(LocalDate from, LocalDate to) {
        if (!to.isAfter(from)) {
            return 0;
        }
        Set<LocalDate> holidays = new HashSet<>(jdbc.query(
                "SELECT date FROM sys.holiday WHERE active AND date > ? AND date <= ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), Date.valueOf(from), Date.valueOf(to)));
        int days = 0;
        for (LocalDate d = from.plusDays(1); !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(d)) {
                days++;
            }
        }
        return days;
    }
}
