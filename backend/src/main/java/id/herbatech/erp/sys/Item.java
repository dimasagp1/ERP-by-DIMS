package id.herbatech.erp.sys;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** SYS-06 Item master: bahan baku, kemasan, WIP, barang jadi, sparepart, ATK, jasa. */
@Getter
@Setter
@Entity
@Table(name = "item", schema = "sys")
public class Item extends BaseEntity implements Activatable {

    @NotBlank
    @Size(max = 32)
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "RM|PM|WIP|FG|SP|ATK|SVC")
    private String type;

    @NotNull
    private Long uomId;

    private String category;

    private boolean lotTracked = true;

    @PositiveOrZero
    private Integer shelfLifeDays;

    @Pattern(regexp = "AMBIENT|COOL|COLD|B3")
    private String storageClass;

    private boolean halalCritical;

    @Pattern(regexp = "DEVELOPMENT|ACTIVE|BLOCKED|OBSOLETE")
    private String status = "ACTIVE";

    private String description;

    private boolean active = true;
}
