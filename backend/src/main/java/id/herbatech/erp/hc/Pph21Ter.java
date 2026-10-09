package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Tarif efektif rata-rata PPh 21 bulanan (PMK 168/2023). upperLimit kosong = tanpa batas. */
@Getter
@Setter
@Entity
@Table(name = "pph21_ter", schema = "hc")
public class Pph21Ter extends BaseEntity implements Activatable {

    @NotBlank
    @Pattern(regexp = "A|B|C")
    private String category;

    @Positive
    private BigDecimal upperLimit;

    @NotNull
    @PositiveOrZero
    @Max(1)
    private BigDecimal rate;

    private boolean active = true;
}
