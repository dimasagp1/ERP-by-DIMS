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
 * PRE-04 Permintaan bahan sesuai BOM batch ke gudang; diserahkan gudang (SCM-23) per lot FEFO.
 */
@Getter
@Setter
@Entity
@Table(name = "material_request", schema = "pre")
public class MaterialRequest extends DocumentEntity {

    private Long woId;
    private LocalDate neededAt;
    private String notes;
    private Instant issuedAt;
    private Long issuedBy;
    private BigDecimal issuedValue = BigDecimal.ZERO;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "material_request_line", schema = "pre")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "request_id")
        private MaterialRequest request;

        private short lineNo;
        private Long itemId;
        private BigDecimal qtyBom = BigDecimal.ZERO;
        private BigDecimal qty;
        private BigDecimal qtyIssued = BigDecimal.ZERO;
        private String note;
    }
}
