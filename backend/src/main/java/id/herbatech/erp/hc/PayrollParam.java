package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** HC-10 Parameter payroll: tarif & batas BPJS, pembagi lembur, biaya jabatan. */
@Getter
@Setter
@Entity
@Table(name = "payroll_param", schema = "hc")
public class PayrollParam extends BaseEntity implements Activatable {

    @NotBlank
    private String key;

    @NotBlank
    private String value;

    private String description;

    private boolean active = true;
}
