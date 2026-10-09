package id.herbatech.erp.sys;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** SYS-02 Cost center (pemilik: FIN). */
@Getter
@Setter
@Entity
@Table(name = "cost_center", schema = "sys")
public class CostCenter extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotNull
    private Long departmentId;

    private Long plantId;

    private boolean production;

    private boolean active = true;
}
