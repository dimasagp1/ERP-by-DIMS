package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** HC-99 Komponen gaji (pendapatan, potongan, tanggungan perusahaan). */
@Getter
@Setter
@Entity
@Table(name = "salary_component", schema = "hc")
public class SalaryComponent extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "EARNING|DEDUCTION|EMPLOYER")
    private String kind;

    private boolean fixed = true;

    private boolean taxable = true;

    @NotBlank
    private String accountCode;

    private short seq = 100;

    private boolean system;

    private boolean active = true;
}
