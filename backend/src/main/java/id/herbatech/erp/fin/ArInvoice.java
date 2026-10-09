package id.herbatech.erp.fin;

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

/** FIN-20 Faktur penjualan & piutang. M1: input manual; mulai M2 dibentuk dari surat jalan (SCM-26). */
@Getter
@Setter
@Entity
@Table(name = "ar_invoice", schema = "fin")
public class ArInvoice extends DocumentEntity {

    private Long partnerId;
    private LocalDate dueDate;
    private String taxInvoiceNo;
    private String customerPo;
    private String description;
    private boolean withPpn = true;
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal ppnAmount = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private BigDecimal receivedAmount = BigDecimal.ZERO;
    /** Tertahan karena limit kredit / piutang lewat tempo (PRD FIN aturan 6). */
    private boolean creditHold;
    /** MANUAL / DO (dibentuk otomatis dari surat jalan SCM-26). */
    private String sourceType = "MANUAL";
    private Long deliveryId;
    private boolean taxReported;
    private String holdReason;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    public BigDecimal outstanding() {
        return total.subtract(receivedAmount);
    }

    @Getter
    @Setter
    @Entity
    @Table(name = "ar_invoice_line", schema = "fin")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "invoice_id")
        private ArInvoice invoice;

        private short lineNo;
        private Long itemId;
        private String description;
        private BigDecimal qty = BigDecimal.ONE;
        private BigDecimal unitPrice = BigDecimal.ZERO;
        private BigDecimal discountPct = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
        private Long revenueAccountId;
    }
}
