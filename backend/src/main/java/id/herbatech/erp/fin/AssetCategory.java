package id.herbatech.erp.fin;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** FIN-40 Kategori aset: akun, umur, metode, kelompok fiskal. */
@Getter
@Setter
@Entity
@Table(name = "asset_category", schema = "fin")
public class AssetCategory extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotNull
    private Long assetAccountId;

    @NotNull
    private Long accumAccountId;

    @NotNull
    private Long expenseAccountId;

    @Positive
    private int usefulLifeMonths;

    @NotBlank
    @Pattern(regexp = "SL|DDB")
    private String method = "SL";

    @NotBlank
    @Pattern(regexp = "KEL1|KEL2|KEL3|KEL4|BANGUNAN_P|BANGUNAN_NP")
    private String fiscalGroup;

    private boolean active = true;
}
