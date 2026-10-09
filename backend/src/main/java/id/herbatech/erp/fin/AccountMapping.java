package id.herbatech.erp.fin;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Mapping akun otomatis per jenis transaksi (FIN-99), mis. GR_RM: D Persediaan bahan baku / K GRNI. */
@Getter
@Setter
@Entity
@Table(name = "account_mapping", schema = "fin")
public class AccountMapping extends BaseEntity implements Activatable {

    @NotBlank
    private String txnType;

    @NotBlank
    private String name;

    @NotNull
    private Long debitAccountId;

    @NotNull
    private Long creditAccountId;

    private boolean active = true;
}
