package id.herbatech.erp.sys;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** SYS-07 Konversi satuan (umum atau khusus item). */
@Getter
@Setter
@Entity
@Table(name = "uom_conversion", schema = "sys")
public class UomConversion extends BaseEntity implements Activatable {

    @NotNull
    private Long fromUomId;

    @NotNull
    private Long toUomId;

    @NotNull
    @Positive
    private BigDecimal factor;

    private Long itemId;

    private boolean active = true;
}
