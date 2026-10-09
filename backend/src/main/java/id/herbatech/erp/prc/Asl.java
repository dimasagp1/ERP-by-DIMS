package id.herbatech.erp.prc;

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

/** PRC-04 Daftar supplier disetujui: kombinasi item × supplier × pabrikan yang boleh dibeli. */
@Getter
@Setter
@Entity
@Table(name = "asl", schema = "prc")
public class Asl extends BaseEntity implements Activatable {

    @NotNull
    private Long itemId;

    @NotNull
    private Long partnerId;

    @NotBlank
    private String manufacturer = "-";

    @NotBlank
    @Pattern(regexp = "APPROVED|CONDITIONAL|BLOCKED")
    private String status = "APPROVED";

    private LocalDate validUntil;

    private String notes;

    private boolean active = true;
}
