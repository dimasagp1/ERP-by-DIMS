package id.herbatech.erp.scm;

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
import java.util.ArrayList;
import java.util.List;

/**
 * SCM-26 Pengiriman & surat jalan: pick per lot FEFO, cek kredit, HPP, faktur penjualan otomatis (FIN-20).
 */
@Getter
@Setter
@Entity
@Table(name = "delivery", schema = "scm")
public class Delivery extends DocumentEntity {

    private Long partnerId;
    private Long soId;
    private Long expeditionPartnerId;
    private String vehicleNo;
    private String driver;
    private String shipTo;
    private String notes;
    private boolean creditHold;
    private String holdReason;
    private BigDecimal totalValue = BigDecimal.ZERO;
    private BigDecimal totalCost = BigDecimal.ZERO;
    private Long arInvoiceId;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "delivery_line", schema = "scm")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "delivery_id")
        private Delivery delivery;

        private short lineNo;
        private Long soLineId;
        private Long itemId;
        private Long lotId;
        private Long locationId;
        private BigDecimal qty;
        private BigDecimal unitPrice = BigDecimal.ZERO;
        private BigDecimal unitCost = BigDecimal.ZERO;
    }
}
