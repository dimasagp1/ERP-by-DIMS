package id.herbatech.erp.sys;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** SYS-09 Lokasi bin dengan zona, kelas suhu, karantina, B3. */
@Getter
@Setter
@Entity
@Table(name = "location", schema = "sys")
public class Location extends BaseEntity implements Activatable {

    @NotNull
    private Long warehouseId;

    @NotBlank
    private String binCode;

    private String zone;

    @Pattern(regexp = "AMBIENT|COOL|COLD")
    private String tempClass = "AMBIENT";

    @Column(name = "is_quarantine")
    private boolean quarantine;

    @Column(name = "is_b3")
    private boolean b3;

    private boolean active = true;
}
