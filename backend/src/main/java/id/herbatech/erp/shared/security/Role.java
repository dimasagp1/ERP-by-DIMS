package id.herbatech.erp.shared.security;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Peran standar (PRD §2). Aksi bawaan disimpan sebagai daftar dipisah koma. */
@Getter
@Setter
@Entity
@Table(name = "role", schema = "sys")
public class Role extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    private ViewScope viewScope;

    @NotBlank
    private String actions;

    private boolean builtin;

    private boolean active = true;

    public Set<Action> actionSet() {
        if (actions == null || actions.isBlank()) {
            return EnumSet.noneOf(Action.class);
        }
        return Arrays.stream(actions.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Action::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Action.class)));
    }
}
