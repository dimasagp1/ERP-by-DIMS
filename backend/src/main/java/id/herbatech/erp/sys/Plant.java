package id.herbatech.erp.sys;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** SYS-01 Plant & site. */
@Getter
@Setter
@Entity
@Table(name = "plant", schema = "sys")
public class Plant extends BaseEntity implements Activatable {

    @NotNull
    private Long companyId;

    @NotBlank
    @Size(max = 8)
    @Pattern(regexp = "[A-Z0-9]+", message = "Kode plant hanya huruf besar dan angka")
    private String code;

    @NotBlank
    private String name;

    private String address;

    private boolean active = true;
}
