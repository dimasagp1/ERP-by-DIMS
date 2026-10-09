package id.herbatech.erp.pre;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** PRE-99 Lini / work center: proses utama (dasar cek kualifikasi), cost center, kapasitas. */
@Getter
@Setter
@Entity
@Table(name = "line", schema = "pre")
public class ProductionLine extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotNull
    private Long plantId;

    private Long costCenterId;

    @NotBlank
    private String processCode;

    @Positive
    private BigDecimal capacityPerShift;

    private String capacityUom;

    private boolean active = true;
}
