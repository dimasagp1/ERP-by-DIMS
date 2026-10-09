package id.herbatech.erp.hc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** HC-08 / ESS-02 Surat perintah lembur; satu dokumen bisa untuk satu tim (baris per karyawan). */
@Getter
@Setter
@Entity
@Table(name = "overtime_request", schema = "hc")
public class OvertimeRequest extends DocumentEntity {

    private LocalDate workDate;
    /** WORKDAY / RESTDAY / HOLIDAY — menentukan pengali upah lembur (PP 35/2021). */
    private String dayType = "WORKDAY";
    private String reason;
    private String reference;
    private BigDecimal totalHours = BigDecimal.ZERO;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "overtime_line", schema = "hc")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "request_id")
        private OvertimeRequest request;

        private short lineNo;
        private Long employeeId;
        private LocalTime startTime;
        private LocalTime endTime;
        private BigDecimal hours;
        private BigDecimal actualHours;
    }
}
