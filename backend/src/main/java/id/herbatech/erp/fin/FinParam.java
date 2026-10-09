package id.herbatech.erp.fin;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** FIN-99 Parameter keuangan: tarif PPN, aturan kredit, kontrol anggaran. */
@Getter
@Setter
@Entity
@Table(name = "fin_param", schema = "fin")
public class FinParam extends BaseEntity implements Activatable {

    @NotBlank
    private String key;

    @NotBlank
    private String value;

    private String description;

    private boolean active = true;
}
