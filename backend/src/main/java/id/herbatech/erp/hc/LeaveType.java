package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** HC-99 Jenis cuti & izin. */
@Getter
@Setter
@Entity
@Table(name = "leave_type", schema = "hc")
public class LeaveType extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "CUTI|IZIN|SAKIT|DINAS")
    private String attendanceStatus;

    private boolean paid = true;

    private boolean deductsAnnual;

    @Positive
    private Integer maxDays;

    private boolean requiresAttachment;

    private boolean active = true;
}
