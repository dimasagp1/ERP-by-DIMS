package id.herbatech.erp.fin;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** FIN-02 Bagan akun. Akun header (postable = false) hanya untuk pengelompokan laporan. */
@Getter
@Setter
@Entity
@Table(name = "account", schema = "fin")
public class Account extends BaseEntity implements Activatable {

    @NotBlank
    @Pattern(regexp = "[0-9A-Z.-]{1,16}", message = "Kode akun hanya angka, huruf besar, titik, tanda hubung")
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "ASSET|LIABILITY|EQUITY|REVENUE|EXPENSE")
    private String type;

    @NotBlank
    @Pattern(regexp = "[DC]")
    private String normalBalance;

    private Long parentId;
    private boolean postable = true;
    private boolean requiresCostCenter;
    private boolean active = true;
}
