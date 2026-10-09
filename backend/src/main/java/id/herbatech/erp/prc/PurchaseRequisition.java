package id.herbatech.erp.prc;

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
import java.util.ArrayList;
import java.util.List;

/**
 * PRC-02 Purchase Requisition dari MRP, departemen, atau Layanan Saya (ESS-04). Disetujui = siap dibelikan;
 * Selesai saat semua baris sudah menjadi PO.
 */
@Getter
@Setter
@Entity
@Table(name = "pr", schema = "prc")
public class PurchaseRequisition extends DocumentEntity {

    private String source = "MANUAL";
    private Long costCenterId;
    private LocalDate needDate;
    private String purpose;
    private BigDecimal totalEst = BigDecimal.ZERO;

    @OneToMany(mappedBy = "requisition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "pr_line", schema = "prc")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "pr_id")
        private PurchaseRequisition requisition;

        private short lineNo;
        private Long itemId;
        private String description;
        private BigDecimal qty;
        private LocalDate needDate;
        private BigDecimal estPrice = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
        private Long costCenterId;
        private Long accountId;
        private Long suggestedPartnerId;
        private BigDecimal qtyOrdered = BigDecimal.ZERO;
        private boolean closed;
    }
}
