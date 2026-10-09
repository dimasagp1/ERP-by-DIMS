package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** PTKP tahunan per status & kategori TER. */
@Getter
@Setter
@Entity
@Table(name = "ptkp", schema = "hc")
public class Ptkp extends BaseEntity implements Activatable {

    @NotBlank
    @Pattern(regexp = "(TK|K)/[0-3]")
    private String status;

    @NotNull
    @PositiveOrZero
    private BigDecimal amount;

    @NotBlank
    @Pattern(regexp = "A|B|C")
    private String terCategory;

    private boolean active = true;
}
