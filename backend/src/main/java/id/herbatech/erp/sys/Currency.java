package id.herbatech.erp.sys;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** SYS-11 Mata uang. */
@Getter
@Setter
@Entity
@Table(name = "currency", schema = "sys")
public class Currency extends BaseEntity implements Activatable {

    @NotBlank
    @Size(min = 3, max = 3)
    private String code;

    @NotBlank
    private String name;

    private String symbol;

    private short decimals = 2;

    private boolean active = true;
}
