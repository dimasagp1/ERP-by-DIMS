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
 * SCM-27 Retur pelanggan: barang masuk lokasi karantina (Hold menunggu keputusan QA), nota kredit ke piutang.
 */
@Getter
@Setter
@Entity
@Table(name = "customer_return", schema = "scm")
public class CustomerReturn extends DocumentEntity {

    private Long partnerId;
    private Long deliveryId;
    private String reason;
    private boolean withPpn = true;
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal ppnAmount = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "customerReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "customer_return_line", schema = "scm")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "return_id")
        private CustomerReturn customerReturn;

        private short lineNo;
        private Long deliveryLineId;
        private Long itemId;
        private Long lotId;
        private BigDecimal qty;
        private Long locationId;
        private BigDecimal unitPrice = BigDecimal.ZERO;
        private BigDecimal unitCost = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
    }
}
