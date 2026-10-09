package id.herbatech.erp.scm;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** SCM-43 Parameter stok per item & plant: min/max, titik pesan ulang, stok pengaman, lead time, MOQ. */
@Getter
@Setter
@Entity
@Table(name = "stock_param", schema = "scm")
public class StockParam extends BaseEntity implements Activatable {

    @NotNull
    private Long plantId;

    @NotNull
    private Long itemId;

    @NotNull
    private BigDecimal minQty = BigDecimal.ZERO;

    private BigDecimal maxQty;

    @NotNull
    private BigDecimal reorderPoint = BigDecimal.ZERO;

    @NotNull
    private BigDecimal safetyStock = BigDecimal.ZERO;

    private int leadTimeDays = 14;

    @NotNull
    private BigDecimal moq = BigDecimal.ZERO;

    @NotNull
    private BigDecimal lotSize = BigDecimal.ZERO;

    private boolean active = true;
}
