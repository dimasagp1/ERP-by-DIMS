package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** HC-03 Data karyawan (bagian inti; data pribadi & payroll ditambah di M1). */
@Getter
@Setter
@Entity
@Table(name = "employee", schema = "hc")
public class Employee extends BaseEntity implements Activatable {

    @NotBlank
    private String nik;

    @NotBlank
    private String name;

    @Email
    private String email;

    private String phone;
    private Long positionId;
    private Long departmentId;
    private Long costCenterId;
    private Long plantId;

    @NotNull
    private LocalDate joinDate;

    private LocalDate endDate;

    @Pattern(regexp = "PKWT|PKWTT|OS")
    private String employment = "PKWTT";

    @Pattern(regexp = "ACTIVE|RESIGNED|TERMINATED")
    private String status = "ACTIVE";

    private String ktpNo;
    private String npwp;

    /** Status PTKP: TK/0..TK/3, K/0..K/3 — dasar kategori TER PPh 21. */
    @Pattern(regexp = "(TK|K)/[0-3]")
    private String ptkpStatus = "TK/0";

    private LocalDate birthDate;

    @Pattern(regexp = "L|P")
    private String gender;

    private String address;
    private String bankName;
    private String bankAccountNo;
    private String bankAccountName;
    private String bpjsKesNo;
    private String bpjsTkNo;
    private Long defaultShiftId;

    /** Pengganti approval saat cuti (PRD §13: approver yang cuti didelegasikan). */
    private Long delegateId;
    private LocalDate delegateUntil;

    private boolean active = true;
}
