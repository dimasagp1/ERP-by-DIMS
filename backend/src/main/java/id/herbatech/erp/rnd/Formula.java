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
@Table(name = "formula", schema = "rnd")
public class Formula extends DocumentEntity {
    private Long productItemId;
    private String formulaVersion;
    private BigDecimal batchSize;
    private Long uomId;
    private BigDecimal targetYieldPct;
    private String notes;
}
