package id.herbatech.erp.rnd;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "cost_estimate", schema = "rnd")
public class CostEstimate extends DocumentEntity {
    private Long formulaId;
    private BigDecimal materialCost;
    private BigDecimal overheadCost;
    private BigDecimal packagingCost;
    private BigDecimal totalCost;
    private BigDecimal targetMarginPct;
    private BigDecimal suggestedPrice;
}
