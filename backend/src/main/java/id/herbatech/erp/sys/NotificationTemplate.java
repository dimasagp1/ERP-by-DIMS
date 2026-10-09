package id.herbatech.erp.sys;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** SYS-15 Template notifikasi, email, dan cetak. */
@Getter
@Setter
@Entity
@Table(name = "notification_template", schema = "sys")
public class NotificationTemplate extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "BELL|EMAIL|PRINT")
    private String channel;

    private String subject;

    @NotBlank
    private String body;

    private boolean active = true;
}
