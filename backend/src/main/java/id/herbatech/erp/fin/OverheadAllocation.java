package id.herbatech.erp.fin;

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
 * FIN-55 Alokasi overhead: beban cost center pendukung dibagi ke cost center produksi menurut jam kerja atau output.
 */
@Getter
@Setter
@Entity
@Table(name = "overhead_alloc", schema = "fin")
public class OverheadAllocation extends DocumentEntity {

    private String period;
    private String basis = "LABOR_HOURS";
    private String notes;
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "allocation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "overhead_alloc_line", schema = "fin")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "alloc_id")
        private OverheadAllocation allocation;

        private short lineNo;
        private Long sourceCcId;
        private Long accountId;
        private Long targetCcId;
        private BigDecimal basisQty = BigDecimal.ZERO;
        private BigDecimal sharePct = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
    }
}
