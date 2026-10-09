package id.herbatech.erp.shared.security;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Pengecualian per menu × aksi yang menimpa aksi bawaan peran. */
@Getter
@Setter
@Entity
@Table(name = "role_menu_permission", schema = "sys")
public class RoleMenuPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long roleId;
    private String menuCode;

    @Enumerated(EnumType.STRING)
    private Action action;

    private boolean allowed;
}
