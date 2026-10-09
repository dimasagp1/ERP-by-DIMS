package id.herbatech.erp.sys;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** SYS-10 Hari libur (plant kosong = semua plant). */
@Getter
@Setter
@Entity
@Table(name = "holiday", schema = "sys")
public class Holiday extends BaseEntity implements Activatable {

    private Long plantId;

    @NotNull
    private LocalDate date;

    @NotBlank
    private String name;

    private boolean active = true;
}
