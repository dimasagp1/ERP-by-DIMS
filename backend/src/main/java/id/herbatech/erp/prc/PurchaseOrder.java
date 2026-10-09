package id.herbatech.erp.prc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * PRC-07 Purchase Order barang & jasa. Disetujui = terbit ke supplier dan anggaran terkunci (komitmen);
 * Selesai saat semua baris diterima atau ditutup.
 */
@Getter
@Setter
@Entity
@Table(name = "po", schema = "prc")
public class PurchaseOrder extends DocumentEntity {

    private Long partnerId;
    private String kind = "GOODS";
    private String currencyCode = "IDR";
    private BigDecimal exchangeRate = BigDecimal.ONE;
    private int paymentTermDays = 30;
    private LocalDate deliveryDate;
    private Long warehouseId;
    private boolean withPpn = true;
    private boolean importPo;
    private Long rfqId;
    private String singleSourceReason;
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal ppnAmount = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private BigDecimal totalIdr = BigDecimal.ZERO;
    private String notes;
    private String closedReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "po_line", schema = "prc")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "po_id")
        private PurchaseOrder order;

        private short lineNo;
        private Long prLineId;
        private Long itemId;
        private String description;
        private BigDecimal qty;
        private BigDecimal unitPrice = BigDecimal.ZERO;
        private BigDecimal discountPct = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
        private LocalDate needDate;
        private LocalDate eta;
        private String followupNote;
        private Long costCenterId;
        private Long accountId;
        private BigDecimal receivedQty = BigDecimal.ZERO;
        private LocalDate firstReceipt;
        private LocalDate lastReceipt;
        private boolean closed;
    }
}
