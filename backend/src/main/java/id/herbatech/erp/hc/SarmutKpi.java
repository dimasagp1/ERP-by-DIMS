package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** HC-13 Indikator Sasaran Mutu (PRD §16). */
@Getter
@Setter
@Entity
@Table(name = "sarmut_kpi", schema = "hc")
public class SarmutKpi extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    private String appCode;

    @NotBlank
    private String unit;

    @NotBlank
    @Pattern(regexp = "HIGHER|LOWER")
    private String direction;

    @NotNull
    private BigDecimal target;

    @NotNull
    @Positive
    private BigDecimal weight = BigDecimal.ONE;

    @NotBlank
    @Pattern(regexp = "AUTO|MANUAL")
    private String source;

    private String autoKey;

    private String formula;

    private boolean active = true;
}
