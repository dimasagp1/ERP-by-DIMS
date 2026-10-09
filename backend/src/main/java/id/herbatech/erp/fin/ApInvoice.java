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

/**
 * FIN-10 Faktur supplier. M1: faktur non-PO (jasa, utilitas, biaya) dengan baris akun beban.
 * Faktur berbasis PO dengan 3-way match PO–GR–faktur aktif saat Procurement & Gudang hadir (M2).
 */
@Getter
@Setter
@Entity
@Table(name = "ap_invoice", schema = "fin")
public class ApInvoice extends DocumentEntity {

    private Long partnerId;
    private String supplierInvoiceNo;
    private String taxInvoiceNo;
    private LocalDate dueDate;
    private String description;
    private String sourceType = "NON_PO";
    private boolean withPpn = true;
    private String pphTaxCode;
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal ppnAmount = BigDecimal.ZERO;
    private BigDecimal pphAmount = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    /** Hutang ke supplier = total − PPh dipotong. */
    private BigDecimal payable = BigDecimal.ZERO;
    private Long poId;
    private boolean taxCredited;
    private Long advancePaymentId;
    private BigDecimal advanceApplied = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    public BigDecimal outstanding() {
        return payable.subtract(advanceApplied).subtract(paidAmount);
    }

    @Getter
    @Setter
    @Entity
    @Table(name = "ap_invoice_line", schema = "fin")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "invoice_id")
        private ApInvoice invoice;

        private short lineNo;
        private Long accountId;
        private Long costCenterId;
        private String description;
        private BigDecimal qty = BigDecimal.ONE;
        private BigDecimal unitPrice = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
        /** Faktur berbasis PO: baris PO, harga PO (Rp), selisih harga % (3-way match). */
        private Long poLineId;
        private BigDecimal poPrice;
        private BigDecimal priceVarPct;
    }
}
