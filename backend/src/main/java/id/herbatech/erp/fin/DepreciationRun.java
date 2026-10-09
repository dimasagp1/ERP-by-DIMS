package id.herbatech.erp.fin;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** FIN-40 Penyusutan satu periode (YYYYMM): baris per aset dihitung oleh {@link AssetService}. */
@Getter
@Setter
@Entity
@Table(name = "depreciation_run", schema = "fin")
public class DepreciationRun extends DocumentEntity {

    private String period;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private BigDecimal totalFiscal = BigDecimal.ZERO;
    private int assetCount;
}
