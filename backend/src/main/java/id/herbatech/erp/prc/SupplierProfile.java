package id.herbatech.erp.prc;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** PRC-03 Profil & kualifikasi supplier: legal, rekening, sertifikat halal; status kualifikasi oleh QA. */
@Getter
@Setter
@Entity
@Table(name = "supplier_profile", schema = "prc")
public class SupplierProfile extends BaseEntity implements Activatable {

    @NotNull
    private Long partnerId;

    private String legalName;

    private String nib;

    private String bankName;

    private String bankAccountNo;

    private String bankAccountName;

    private String contactPerson;

    private String halalCertNo;

    private LocalDate halalCertExpiry;

    private String otherCerts;

    @NotBlank
    @Pattern(regexp = "PENDING|QUALIFIED|CONDITIONAL|DISQUALIFIED")
    private String qualificationStatus = "PENDING";

    private LocalDate qualifiedAt;

    private String notes;

    private boolean active = true;
}
