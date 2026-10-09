package id.herbatech.erp.fin;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** FIN-63 Koreksi fiskal manual (beda tetap / beda waktu) per tahun pajak. */
@Getter
@Setter
@Entity
@Table(name = "fiscal_correction", schema = "fin")
public class FiscalCorrection extends BaseEntity implements Activatable {

    private Long plantId;

    private short year;

    @NotBlank
    private String description;

    @NotBlank
    @Pattern(regexp = "POSITIVE|NEGATIVE")
    private String kind;

    @NotBlank
    @Pattern(regexp = "TETAP|WAKTU")
    private String category = "TETAP";

    private Long accountId;

    @NotNull
    private BigDecimal amount = BigDecimal.ZERO;

    private String notes;

    private boolean active = true;
}
