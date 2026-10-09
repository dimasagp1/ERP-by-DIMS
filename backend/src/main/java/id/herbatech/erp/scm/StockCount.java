package id.herbatech.erp.scm;

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
import java.util.ArrayList;
import java.util.List;

/**
 * SCM-41 Stock opname & cycle count: saldo sistem dibekukan saat lembar hitung dibuat; selisih menjadi penyesuaian saat diposting.
 */
@Getter
@Setter
@Entity
@Table(name = "stock_count", schema = "scm")
public class StockCount extends DocumentEntity {

    private Long warehouseId;
    private String kind = "CYCLE";
    private String notes;
    private int linesTotal;
    private int linesAccurate;
    private BigDecimal diffValue = BigDecimal.ZERO;

    @OneToMany(mappedBy = "count", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "stock_count_line", schema = "scm")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "count_id")
        private StockCount count;

        private short lineNo;
        private Long itemId;
        private Long lotId;
        private Long locationId;
        private BigDecimal systemQty = BigDecimal.ZERO;
        private BigDecimal countedQty;
        private BigDecimal diffQty = BigDecimal.ZERO;
        private BigDecimal unitCost = BigDecimal.ZERO;
        private BigDecimal diffValue = BigDecimal.ZERO;
        private String note;
    }
}
