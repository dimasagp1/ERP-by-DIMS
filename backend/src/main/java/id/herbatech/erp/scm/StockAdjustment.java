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
 * SCM-42 Penyesuaian stok dengan alasan & approval berjenjang (IC → SCM → FIN di atas batas).
 */
@Getter
@Setter
@Entity
@Table(name = "stock_adjustment", schema = "scm")
public class StockAdjustment extends DocumentEntity {

    private String reason;
    private Long countId;
    private BigDecimal totalValue = BigDecimal.ZERO;

    @OneToMany(mappedBy = "adjustment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "stock_adjustment_line", schema = "scm")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "adjustment_id")
        private StockAdjustment adjustment;

        private short lineNo;
        private Long itemId;
        private Long lotId;
        private Long locationId;
        private BigDecimal qtyDelta;
        private BigDecimal unitCost = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
        private String note;
    }
}
