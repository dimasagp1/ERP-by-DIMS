package id.herbatech.erp.sys;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** SYS-01 Perusahaan (entitas legal). */
@Getter
@Setter
@Entity
@Table(name = "company", schema = "sys")
public class Company extends BaseEntity implements Activatable {

    @NotBlank
    @Size(max = 16)
    private String code;

    @NotBlank
    private String name;

    private String npwp;

    private String address;

    private boolean active = true;
}
