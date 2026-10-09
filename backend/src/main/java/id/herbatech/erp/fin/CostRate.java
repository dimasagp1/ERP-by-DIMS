package id.herbatech.erp.fin;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** FIN-52/53 Tarif per jam tenaga kerja langsung & overhead per cost center produksi. */
@Getter
@Setter
@Entity
@Table(name = "cost_rate", schema = "fin")
public class CostRate extends BaseEntity implements Activatable {

    @NotNull
    private Long plantId;

    @NotNull
    private Long costCenterId;

    @NotNull
    private LocalDate validFrom;

    @NotNull
    private BigDecimal laborRate = BigDecimal.ZERO;

    @NotNull
    private BigDecimal overheadRate = BigDecimal.ZERO;

    @NotBlank
    private String source = "MANUAL";

    private boolean active = true;
}
