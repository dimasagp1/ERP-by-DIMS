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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * SCM-02 Pesanan pelanggan: cek stok tersedia & kredit customer. Disetujui = dikonfirmasi; Selesai saat terkirim penuh.
 */
@Getter
@Setter
@Entity
@Table(name = "so", schema = "scm")
public class SalesOrder extends DocumentEntity {

    private Long partnerId;
    private String customerPo;
    private LocalDate deliveryDate;
    private String shipTo;
    private boolean withPpn = true;
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal ppnAmount = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private boolean creditHold;
    private String holdReason;
    private String notes;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "so_line", schema = "scm")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "so_id")
        private SalesOrder order;

        private short lineNo;
        private Long itemId;
        private BigDecimal qty;
        private BigDecimal unitPrice = BigDecimal.ZERO;
        private BigDecimal discountPct = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
        private LocalDate deliveryDate;
        private BigDecimal qtyShipped = BigDecimal.ZERO;
        private boolean closed;
    }
}
