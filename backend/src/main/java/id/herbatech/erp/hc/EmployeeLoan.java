package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** HC-14 Pinjaman & kasbon karyawan; cicilan dipotong lewat payroll. */
@Getter
@Setter
@Entity
@Table(name = "employee_loan", schema = "hc")
public class EmployeeLoan extends DocumentEntity {

    private Long employeeId;
    /** PINJAMAN / KASBON. */
    private String kind = "PINJAMAN";
    private BigDecimal principal;
    private int installments = 1;
    private BigDecimal installmentAmount = BigDecimal.ZERO;
    /** Periode potongan pertama, YYYYMM. */
    private String startPeriod;
    private BigDecimal repaidAmount = BigDecimal.ZERO;
    private String purpose;
}
