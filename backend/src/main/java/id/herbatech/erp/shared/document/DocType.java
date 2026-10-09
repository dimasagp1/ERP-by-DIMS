package id.herbatech.erp.shared.document;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** SYS-05 Jenis dokumen: prefix penomoran, aplikasi/menu pemilik, dan apakah wajib tanda tangan elektronik. */
@Getter
@Setter
@Entity
@Table(name = "doc_type", schema = "sys")
public class DocType extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    private String appCode;

    @NotBlank
    private String menuCode;

    @NotBlank
    @Pattern(regexp = "[A-Z0-9-]{1,16}", message = "Prefix hanya huruf besar, angka, dan tanda hubung")
    private String prefix;

    /** MONTHLY, YEARLY, atau NEVER. */
    @Pattern(regexp = "MONTHLY|YEARLY|NEVER")
    private String resetPeriod = "MONTHLY";

    private boolean requiresEsign;

    /**
     * Menu Layanan Saya (mis. ESS-01) bila dokumen ini bisa dibuat karyawan sendiri. Pembuatnya boleh membuat,
     * mengubah draft, mengajukan, dan melihat dokumennya tanpa hak di aplikasi departemen pemilik.
     */
    private String essMenuCode;

    private boolean active = true;
}
