package id.herbatech.erp.pre;

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

/** PRE-99 Parameter produksi per produk: awalan batch, ukuran batch standar, yield minimum. */
@Getter
@Setter
@Entity
@Table(name = "product_param", schema = "pre")
public class ProductParam extends BaseEntity implements Activatable {

    @NotNull
    private Long itemId;

    @NotBlank
    @Pattern(regexp = "[A-Z0-9]{1,8}")
    private String batchPrefix;

    @NotNull
    @Positive
    private BigDecimal stdBatchSize;

    @NotNull
    @PositiveOrZero
    @Max(100)
    private BigDecimal minYieldPct;

    private Long defaultLineId;

    private boolean active = true;
}
