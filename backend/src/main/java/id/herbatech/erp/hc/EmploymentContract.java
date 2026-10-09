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

/** HC-06 Kontrak & status kepegawaian. */
@Getter
@Setter
@Entity
@Table(name = "employment_contract", schema = "hc")
public class EmploymentContract extends BaseEntity implements Activatable {

    @NotNull
    private Long employeeId;

    @NotBlank
    private String contractNo;

    @NotBlank
    @Pattern(regexp = "PKWT|PKWTT|OS|MAGANG")
    private String type;

    @NotNull
    private LocalDate startDate;

    private LocalDate endDate;

    private Long positionId;

    private String note;

    private boolean active = true;
}
