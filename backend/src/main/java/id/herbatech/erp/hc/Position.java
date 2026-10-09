package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** HC-02 Posisi. {@code reportsToId} menentukan atasan langsung untuk approval. */
@Getter
@Setter
@Entity
@Table(name = "position", schema = "hc")
public class Position extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String title;

    @NotNull
    private Long departmentId;

    private Long reportsToId;
    private String grade;
    private boolean active = true;
}
