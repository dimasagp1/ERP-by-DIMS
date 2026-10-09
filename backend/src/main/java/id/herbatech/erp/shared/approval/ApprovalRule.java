package id.herbatech.erp.shared.approval;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SYS-04 Matriks approval: satu baris = satu level untuk satu jenis dokumen. Level berlaku bila nilai dokumen
 * ≥ {@code minAmount}. Approver: atasan langsung pembuat (HC-02), pemegang peran di aplikasi, atau pengguna tertentu.
 */
@Getter
@Setter
@Entity
@Table(name = "approval_rule", schema = "sys")
public class ApprovalRule extends BaseEntity implements Activatable {

    @NotBlank
    private String docTypeCode;

    @Min(1)
    @Max(5)
    private short level;

    @NotNull
    @PositiveOrZero
    private BigDecimal minAmount = BigDecimal.ZERO;

    @NotBlank
    @Pattern(regexp = "DIRECT_SUPERIOR|ROLE|USER")
    private String approverType;

    private String approverRole;
    private String approverApp;
    private Long approverUserId;
    private Long plantId;

    /** Level hanya berlaku bila dokumen membawa penanda ini (mis. ABROAD, CREDIT_HOLD, OVER_BUDGET). */
    private String conditionKey;

    @NotBlank
    private String label;

    private boolean active = true;
}
