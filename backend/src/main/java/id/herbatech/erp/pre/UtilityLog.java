package id.herbatech.erp.pre;

import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "utility_log", schema = "pre")
public class UtilityLog extends BaseEntity {
    private LocalDate logDate;
    private String shiftName;
    private BigDecimal electricityKwh;
    private BigDecimal waterM3;
    private BigDecimal steamBar;
    private BigDecimal hvacTempC;
    private BigDecimal hvacHumidityRh;
    private BigDecimal hvacPressureDiffPa;
}
