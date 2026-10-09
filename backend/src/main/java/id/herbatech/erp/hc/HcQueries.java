package id.herbatech.erp.hc;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API baca HC untuk modul lain (Produksi): kualifikasi operator & jam kerja dari absensi. */
@Service
public class HcQueries {

    private final QualificationCheck qualifications;
    private final AttendanceService attendance;
    private final HcSupport hc;

    HcQueries(QualificationCheck qualifications, AttendanceService attendance, HcSupport hc) {
        this.qualifications = qualifications;
        this.attendance = attendance;
        this.hc = hc;
    }

    /** Operator berkualifikasi aktif untuk proses pada tanggal itu (PRE aturan 1, HC aturan 2). */
    public boolean isQualified(Long employeeId, String processCode, LocalDate onDate) {
        return qualifications.isQualified(employeeId, processCode, onDate);
    }

    /** Jam kerja tercatat (status HADIR) atau null bila tidak ada absensi. */
    public BigDecimal workHours(Long employeeId, LocalDate date) {
        return attendance.workHours(employeeId, date);
    }

    public String employeeName(Long employeeId) {
        return hc.employeeName(employeeId);
    }

    public void requireActive(Long employeeId) {
        hc.requireActiveEmployee(employeeId);
    }
}
