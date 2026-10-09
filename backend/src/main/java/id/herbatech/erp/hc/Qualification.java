package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** HC-12 Kualifikasi operator per proses/lini dengan masa berlaku (PRE aturan 1). */
@Getter
@Setter
@Entity
@Table(name = "qualification", schema = "hc")
public class Qualification extends BaseEntity implements Activatable {

    @NotNull
    private Long employeeId;

    @NotBlank
    private String processCode;

    private Long lineId;

    @NotNull
    private LocalDate validFrom;

    @NotNull
    private LocalDate validUntil;

    @NotBlank
    @Pattern(regexp = "TRAINING|ASESMEN|SERTIFIKASI")
    private String basis = "ASESMEN";

    private String note;

    private boolean active = true;
}
