package id.herbatech.erp.pre;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * PRE-02 Work Order produksi. Disetujui = dirilis ke lini (nomor batch & kedaluwarsa terbit); Selesai saat hasil
 * produksi (PRE-08) diposting. Mulai M2 WO dirilis dari PPIC (SCM-07).
 */
@Getter
@Setter
@Entity
@Table(name = "work_order", schema = "pre")
public class WorkOrder extends DocumentEntity {

    private Long productItemId;
    private String batchNo;
    private BigDecimal qtyPlan;
    private Long lineId;
    private Long shiftId;
    private LocalDate plannedStart;
    private LocalDate plannedEnd;
    private LocalDate mfgDate;
    private LocalDate expDate;
    private Instant startedAt;
    private Instant finishedAt;
    private BigDecimal qtyGood = BigDecimal.ZERO;
    private BigDecimal qtyReject = BigDecimal.ZERO;
    private BigDecimal yieldPct;
    private String source = "MANUAL";
    private String notes;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Operator> operators = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "wo_operator", schema = "pre")
    public static class Operator {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "wo_id")
        private WorkOrder workOrder;

        private short lineNo;
        private Long employeeId;
        /** OPERATOR / CHECKER / LEADER. */
        private String role = "OPERATOR";
        private String processCode;
    }
}
