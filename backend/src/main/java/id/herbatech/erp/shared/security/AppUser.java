package id.herbatech.erp.shared.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** SYS-03 Pengguna. Kata sandi hanya disimpan sebagai hash BCrypt dan tidak pernah dikirim ke klien. */
@Getter
@Setter
@Entity
@Table(name = "app_user", schema = "sys")
public class AppUser extends BaseEntity implements Activatable {

    @NotBlank
    @Size(max = 64)
    private String username;

    @JsonIgnore
    private String passwordHash;

    @NotBlank
    @Size(max = 128)
    private String fullName;

    @Email
    private String email;

    private Long employeeId;
    private Long defaultPlantId;
    private String locale = "id";
    private String theme = "light";
    private String density = "comfortable";
    private String startPage = "launcher";
    private String notifyPrefs = "approval,deadline,rejected,escalation";

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private int failedAttempts;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant lockedUntil;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant lastLoginAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant passwordChangedAt;

    private boolean active = true;
}
