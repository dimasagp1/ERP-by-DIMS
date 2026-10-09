package id.herbatech.erp.sys;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** SYS-11 Kurs harian & kurs pajak (KMK). */
@Getter
@Setter
@Entity
@Table(name = "exchange_rate", schema = "sys")
public class ExchangeRate extends BaseEntity implements Activatable {

    @NotBlank
    private String currencyCode;

    @NotNull
    private LocalDate rateDate;

    @NotNull
    @Positive
    private BigDecimal rate;

    @Positive
    private BigDecimal taxRate;

    private boolean active = true;
}
