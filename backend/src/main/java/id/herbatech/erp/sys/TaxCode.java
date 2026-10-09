package id.herbatech.erp.sys;

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

/** SYS-12 Kode pajak PPN & PPh (pemilik: FIN-Tax). */
@Getter
@Setter
@Entity
@Table(name = "tax_code", schema = "sys")
public class TaxCode extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "PPN|PPH21|PPH22|PPH23|PPH4_2")
    private String type;

    @NotNull
    @PositiveOrZero
    private BigDecimal rate;

    private boolean active = true;
}
