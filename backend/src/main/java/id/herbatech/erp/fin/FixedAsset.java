package id.herbatech.erp.fin;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** FIN-40 Kapitalisasi aset tetap (PRD FIN aturan 7: dikapitalisasi saat BAST/GR, bukan saat PO). */
@Getter
@Setter
@Entity
@Table(name = "fixed_asset", schema = "fin")
public class FixedAsset extends DocumentEntity {

    private String name;
    private Long categoryId;
    private BigDecimal acquisitionCost;
    private BigDecimal residualValue = BigDecimal.ZERO;
    private int usefulLifeMonths;
    private String method = "SL";
    private LocalDate depreciationStart;
    private Long costCenterId;
    private String location;
    private String serialNo;
    private String sourceRef;
    /** Akun lawan kapitalisasi (mis. 2102 GRNI, 2101 hutang, 1111 bank). */
    private Long creditAccountId;
    private BigDecimal accumulated = BigDecimal.ZERO;
    private BigDecimal fiscalAccumulated = BigDecimal.ZERO;
    /** ACTIVE / FULLY_DEP / DISPOSED. */
    private String assetState = "ACTIVE";

    public BigDecimal bookValue() {
        return acquisitionCost.subtract(accumulated);
    }
}
