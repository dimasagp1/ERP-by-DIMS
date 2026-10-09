package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** HC-09 Payroll satu periode (YYYYMM) untuk satu plant. Slip & baris dikelola {@link PayrollService}. */
@Getter
@Setter
@Entity
@Table(name = "payroll_run", schema = "hc")
public class PayrollRun extends DocumentEntity {

    private String period;
    private String description;
    private int employeeCount;
    private BigDecimal totalGross = BigDecimal.ZERO;
    private BigDecimal totalDeduction = BigDecimal.ZERO;
    private BigDecimal totalNet = BigDecimal.ZERO;
    private BigDecimal totalEmployer = BigDecimal.ZERO;
    private BigDecimal totalPph21 = BigDecimal.ZERO;
    private Instant calculatedAt;
    private String warnings;
}
