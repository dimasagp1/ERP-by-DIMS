package id.herbatech.erp.sys;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** SYS-02 Departemen & seksi (pohon via parentId). */
@Getter
@Setter
@Entity
@Table(name = "department", schema = "sys")
public class Department extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private Long parentId;

    private String appCode;

    private boolean active = true;
}
