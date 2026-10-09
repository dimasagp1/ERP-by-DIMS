package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** HC-08 Hak cuti tahunan per karyawan per tahun. */
@Getter
@Setter
@Entity
@Table(name = "leave_entitlement", schema = "hc")
public class LeaveEntitlement extends BaseEntity implements Activatable {

    @NotNull
    private Long employeeId;

    @Min(2000)
    private short year;

    @NotNull
    @PositiveOrZero
    private BigDecimal days;

    @PositiveOrZero
    private BigDecimal carriedOver = BigDecimal.ZERO;

    private boolean active = true;
}
