package id.herbatech.erp.pre;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** PRE-99 Alasan reject & waste. */
@Getter
@Setter
@Entity
@Table(name = "reject_reason", schema = "pre")
public class RejectReason extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "BAHAN|MESIN|PROSES|MANUSIA")
    private String category = "PROSES";

    private boolean active = true;
}
