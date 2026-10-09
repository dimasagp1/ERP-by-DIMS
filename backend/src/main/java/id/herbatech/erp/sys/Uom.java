package id.herbatech.erp.sys;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** SYS-07 Satuan. */
@Getter
@Setter
@Entity
@Table(name = "uom", schema = "sys")
public class Uom extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "MASS|VOLUME|COUNT|LENGTH|TIME")
    private String category;

    private boolean active = true;
}
