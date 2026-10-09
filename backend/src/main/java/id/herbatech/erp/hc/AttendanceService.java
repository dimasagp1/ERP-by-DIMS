package id.herbatech.erp.hc;

import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.integration.IntegrationService;
import id.herbatech.erp.shared.period.PeriodLockService;
import id.herbatech.erp.shared.security.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HC-07 Absensi: catatan harian per karyawan dari mesin absensi, input manual, cuti, dan SPD.
 * Setelah periode dikunci (modul HC), absensi tidak bisa diubah dan menjadi dasar payroll (PRD HC aturan 3).
 */
@Service
public class AttendanceService {

    public static final Set<String> STATUSES = Set.of("HADIR", "IZIN", "SAKIT", "CUTI", "ALPA", "LIBUR", "DINAS");

    public record AttendanceRow(Long id, Long employeeId, String employee, LocalDate workDate, Long shiftId, String shiftCode,
                                LocalTime checkIn, LocalTime checkOut, String status, BigDecimal workHours,
                                int lateMinutes, String source, String sourceDoc, String note) {
    }

    public record ImportResult(int imported, int skipped, List<String> errors) {
    }

    private final JdbcTemplate jdbc;
    private final PeriodLockService periods;
    private final IntegrationService integration;

    public AttendanceService(JdbcTemplate jdbc, PeriodLockService periods, IntegrationService integration) {
        this.jdbc = jdbc;
        this.periods = periods;
        this.integration = integration;
    }

    // ------------------------------------------------------------------ kunci periode

    public boolean isLocked(LocalDate date) {
        return periods.isLocked("HC", date);
    }

    public void assertOpen(LocalDate date) {
        if (isLocked(date)) {
            throw BusinessException.of("ATT_LOCKED", "Absensi periode %02d/%d sudah dikunci untuk payroll",
                    date.getMonthValue(), date.getYear());
        }
    }

    // ------------------------------------------------------------------ hari kerja

    public List<LocalDate> workingDays(LocalDate from, LocalDate to) {
        Set<LocalDate> holidays = new HashSet<>(jdbc.query("SELECT date FROM sys.holiday WHERE active AND date BETWEEN ? AND ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), Date.valueOf(from), Date.valueOf(to)));
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(d)) {
                days.add(d);
            }
        }
        return days;
    }

    // ------------------------------------------------------------------ status dari dokumen (cuti, SPD)

    /** Menandai hari kerja dalam rentang dengan status dari dokumen yang disetujui. */
    @Transactional
    public int applyStatus(Long employeeId, LocalDate from, LocalDate to, String status, String source, String docNo) {
        int n = 0;
        for (LocalDate d : workingDays(from, to)) {
            assertOpen(d);
            jdbc.update("""
                    INSERT INTO hc.attendance (employee_id, work_date, status, source, source_doc, created_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (employee_id, work_date) DO UPDATE SET status = EXCLUDED.status, source = EXCLUDED.source,
                        source_doc = EXCLUDED.source_doc, work_hours = 0, updated_at = now(), updated_by = EXCLUDED.created_by""",
                    employeeId, Date.valueOf(d), status, source, docNo, UserContext.currentOptional().map(u -> u.id()).orElse(null));
            n++;
        }
        return n;
    }

    /** Menghapus status yang berasal dari dokumen yang dibatalkan. */
    @Transactional
    public void removeBySource(String docNo) {
        jdbc.queryForList("SELECT DISTINCT work_date FROM hc.attendance WHERE source_doc = ?", Date.class, docNo)
                .forEach(d -> assertOpen(d.toLocalDate()));
        jdbc.update("DELETE FROM hc.attendance WHERE source_doc = ? AND check_in IS NULL", docNo);
    }

    // ------------------------------------------------------------------ input manual & impor mesin

    @Transactional
    public void upsert(Long employeeId, LocalDate date, Long shiftId, LocalTime in, LocalTime out, String status, String note) {
        if (!STATUSES.contains(status)) {
            throw new BusinessException("ATT_STATUS", "Status absensi tidak dikenal: " + status);
        }
        assertOpen(date);
        Calc c = calc(shiftId, in, out);
        jdbc.update("""
                        INSERT INTO hc.attendance (employee_id, work_date, shift_id, check_in, check_out, status, work_hours,
                                                   late_minutes, source, note, created_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'MANUAL', ?, ?)
                        ON CONFLICT (employee_id, work_date) DO UPDATE SET shift_id = EXCLUDED.shift_id, check_in = EXCLUDED.check_in,
                            check_out = EXCLUDED.check_out, status = EXCLUDED.status, work_hours = EXCLUDED.work_hours,
                            late_minutes = EXCLUDED.late_minutes, source = 'MANUAL', note = EXCLUDED.note,
                            updated_at = now(), updated_by = EXCLUDED.created_by""",
                employeeId, Date.valueOf(date), shiftId, in, out, status, "HADIR".equals(status) ? c.hours : BigDecimal.ZERO,
                "HADIR".equals(status) ? c.late : 0, note, UserContext.userId());
    }

    /**
     * Impor CSV mesin absensi: {@code nik;tanggal;jam_masuk;jam_pulang} (tanggal YYYY-MM-DD atau DD/MM/YYYY).
     * Baris pada periode terkunci atau karyawan tak dikenal dilewati dan dilaporkan.
     */
    @Transactional
    public ImportResult importCsv(InputStream in, String filename) {
        int ok = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int no = 0;
            while ((line = r.readLine()) != null) {
                no++;
                line = line.replace("﻿", "").trim();
                if (line.isEmpty() || (no == 1 && line.toLowerCase().startsWith("nik"))) {
                    continue;
                }
                String[] p = line.split("[;,\t]");
                try {
                    if (p.length < 3) {
                        throw new IllegalArgumentException("kolom kurang");
                    }
                    Map<String, Object> emp = jdbc.queryForList("SELECT id, default_shift_id FROM hc.employee WHERE nik = ?", p[0].trim())
                            .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("NIK " + p[0].trim() + " tidak dikenal"));
                    LocalDate date = parseDate(p[1].trim());
                    if (isLocked(date)) {
                        throw new IllegalArgumentException("periode terkunci");
                    }
                    LocalTime cin = LocalTime.parse(p[2].trim());
                    LocalTime cout = p.length > 3 && !p[3].isBlank() ? LocalTime.parse(p[3].trim()) : null;
                    Long shift = emp.get("default_shift_id") == null ? null : ((Number) emp.get("default_shift_id")).longValue();
                    Calc c = calc(shift, cin, cout);
                    jdbc.update("""
                                    INSERT INTO hc.attendance (employee_id, work_date, shift_id, check_in, check_out, status, work_hours,
                                                               late_minutes, source, created_by)
                                    VALUES (?, ?, ?, ?, ?, 'HADIR', ?, ?, 'MESIN', ?)
                                    ON CONFLICT (employee_id, work_date) DO UPDATE SET check_in = EXCLUDED.check_in,
                                        check_out = EXCLUDED.check_out, status = 'HADIR', work_hours = EXCLUDED.work_hours,
                                        late_minutes = EXCLUDED.late_minutes, source = 'MESIN', updated_at = now()
                                    WHERE hc.attendance.source IN ('MESIN', 'MANUAL')""",
                            ((Number) emp.get("id")).longValue(), Date.valueOf(date), shift, cin, cout, c.hours, c.late,
                            UserContext.userId());
                    ok++;
                } catch (IllegalArgumentException | DateTimeParseException e) {
                    skipped++;
                    if (errors.size() < 50) {
                        errors.add("Baris " + no + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            throw new BusinessException("IMPORT", "File tidak bisa dibaca: " + e.getMessage());
        }
        integration.logInbound("ABSENSI", "/import-csv", filename, skipped == 0 ? "SUCCESS" : "FAILED",
                ok + " baris diimpor, " + skipped + " dilewati", errors.isEmpty() ? null : String.join("\n", errors));
        return new ImportResult(ok, skipped, errors);
    }

    public List<AttendanceRow> list(YearMonth period, Long employeeId, Long departmentId, Long plantId) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.*, e.nik || ' · ' || e.name AS emp, s.code AS shift_code FROM hc.attendance a
                JOIN hc.employee e ON e.id = a.employee_id LEFT JOIN sys.shift s ON s.id = a.shift_id
                WHERE a.work_date BETWEEN ? AND ? AND e.plant_id = ?""");
        List<Object> args = new ArrayList<>(List.of(Date.valueOf(period.atDay(1)), Date.valueOf(period.atEndOfMonth()), plantId));
        if (employeeId != null) {
            sql.append(" AND a.employee_id = ?");
            args.add(employeeId);
        }
        if (departmentId != null) {
            sql.append(" AND e.department_id = ?");
            args.add(departmentId);
        }
        sql.append(" ORDER BY e.nik, a.work_date LIMIT 20000");
        return jdbc.query(sql.toString(), (rs, i) -> new AttendanceRow(rs.getLong("id"), rs.getLong("employee_id"),
                rs.getString("emp"), rs.getDate("work_date").toLocalDate(), (Long) rs.getObject("shift_id"),
                rs.getString("shift_code"), rs.getObject("check_in", LocalTime.class), rs.getObject("check_out", LocalTime.class),
                rs.getString("status"), rs.getBigDecimal("work_hours"), rs.getInt("late_minutes"), rs.getString("source"),
                rs.getString("source_doc"), rs.getString("note")), args.toArray());
    }

    /** Jam kerja tercatat seorang karyawan pada satu tanggal (dasar PRE-12). */
    public BigDecimal workHours(Long employeeId, LocalDate date) {
        return jdbc.queryForList("SELECT work_hours FROM hc.attendance WHERE employee_id = ? AND work_date = ? AND status = 'HADIR'",
                BigDecimal.class, employeeId, Date.valueOf(date)).stream().findFirst().orElse(null);
    }

    private record Calc(BigDecimal hours, int late) {
    }

    /** Jam kerja = selisih masuk-pulang (lintas tengah malam didukung) dikurangi istirahat 1 jam bila > 5 jam. */
    private Calc calc(Long shiftId, LocalTime in, LocalTime out) {
        if (in == null || out == null) {
            return new Calc(BigDecimal.ZERO, 0);
        }
        long minutes = Duration.between(in, out).toMinutes();
        if (minutes < 0) {
            minutes += 24 * 60;
        }
        if (minutes > 5 * 60) {
            minutes -= 60;
        }
        int late = 0;
        if (shiftId != null) {
            LocalTime start = jdbc.queryForList("SELECT start_time FROM sys.shift WHERE id = ?", LocalTime.class, shiftId)
                    .stream().findFirst().orElse(null);
            if (start != null && in.isAfter(start) && Duration.between(start, in).toHours() < 6) {
                late = (int) Duration.between(start, in).toMinutes();
            }
        }
        return new Calc(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP), late);
    }

    private static LocalDate parseDate(String s) {
        if (s.contains("/")) {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
        return LocalDate.parse(s);
    }
}
