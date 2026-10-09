package id.herbatech.erp.shared.security;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Penugasan peran ke pengguna untuk satu aplikasi ('*' = semua) dan plant (null = semua plant). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_role", schema = "sys")
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long roleId;
    private String appCode;
    private Long plantId;

    public UserRole(Long userId, Long roleId, String appCode, Long plantId) {
        this.userId = userId;
        this.roleId = roleId;
        this.appCode = appCode;
        this.plantId = plantId;
    }
}
