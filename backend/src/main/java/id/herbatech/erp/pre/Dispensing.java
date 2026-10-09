package id.herbatech.erp.pre;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "dispensing", schema = "pre")
public class Dispensing extends DocumentEntity {
    private Long woId;
    private Long itemId;
    private Long lotId;
    private BigDecimal targetQty;
    private BigDecimal actualQty;
    private Long weighedBy;
    private Long verifiedBy;
    private String barcodeScanned;
}
