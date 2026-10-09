package id.herbatech.erp.pre;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** PRE-08 Hasil produksi & serah terima: barang jadi selalu masuk lokasi karantina (PRE aturan 6). */
@Getter
@Setter
@Entity
@Table(name = "production_output", schema = "pre")
public class ProductionOutput extends DocumentEntity {

    private Long woId;
    private BigDecimal qtyGood;
    private BigDecimal qtyReject = BigDecimal.ZERO;
    private Long rejectReasonId;
    private BigDecimal yieldPct;
    private String yieldExplanation;
    private Long toLocationId;
    private Long lotId;
    /** Biaya satuan & nilai barang jadi saat diposting (biaya standar FIN-52 atau biaya bahan batch). */
    private BigDecimal unitCost = BigDecimal.ZERO;
    private BigDecimal totalCost = BigDecimal.ZERO;
    /** SCM-24: konfirmasi gudang barang jadi atas serah terima produksi. */
    private BigDecimal receivedQty;
    private Long receivedBy;
    private Instant receivedAt;
    private String receiveNote;
}
