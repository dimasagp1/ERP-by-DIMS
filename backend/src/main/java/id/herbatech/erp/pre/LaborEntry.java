package id.herbatech.erp.pre;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** PRE-12 Jam orang per WO (dasar biaya tenaga kerja batch). */
@Getter
@Setter
@Entity
@Table(name = "labor_entry", schema = "pre")
public class LaborEntry extends BaseEntity implements Activatable {

    @NotNull
    private Long woId;

    @NotNull
    private Long employeeId;

    @NotNull
    private LocalDate workDate;

    @NotNull
    @Positive
    @Max(16)
    private BigDecimal hours;

    private String activity;

    private boolean active = true;
}
