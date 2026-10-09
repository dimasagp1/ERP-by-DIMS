package id.herbatech.erp.sys;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** SYS-09 Gudang (pemilik: SCM-WH). */
@Getter
@Setter
@Entity
@Table(name = "warehouse", schema = "sys")
public class Warehouse extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotNull
    private Long plantId;

    @NotBlank
    @Pattern(regexp = "RM|PM|FG|SP|GENERAL|TRANSIT")
    private String type;

    private boolean active = true;
}
