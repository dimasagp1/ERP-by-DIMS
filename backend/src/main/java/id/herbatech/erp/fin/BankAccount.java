package id.herbatech.erp.fin;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** FIN-99 Rekening bank & kas, terhubung ke akun GL. */
@Getter
@Setter
@Entity
@Table(name = "bank_account", schema = "fin")
public class BankAccount extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "BANK|KAS")
    private String kind;

    private String bankName;

    private String accountNo;

    @NotBlank
    private String currencyCode = "IDR";

    @NotNull
    private Long glAccountId;

    private Long plantId;

    private boolean active = true;
}
