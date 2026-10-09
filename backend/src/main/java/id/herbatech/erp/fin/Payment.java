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
import java.util.ArrayList;
import java.util.List;

/** FIN-12 Pembayaran hutang (AP, dengan alokasi ke faktur) atau FIN-11 uang muka pembelian (ADVANCE). */
@Getter
@Setter
@Entity
@Table(name = "payment", schema = "fin")
public class Payment extends DocumentEntity {

    private String kind = "AP";
    private Long partnerId;
    private Long bankAccountId;
    private String method = "TRANSFER";
    private String reference;
    private String description;
    private BigDecimal amount = BigDecimal.ZERO;
    /** Untuk uang muka: bagian yang sudah dipotongkan ke faktur. */
    private BigDecimal advanceUsed = BigDecimal.ZERO;
    private boolean reconciled;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Allocation> allocations = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "payment_allocation", schema = "fin")
    public static class Allocation {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "payment_id")
        private Payment payment;

        private short lineNo;
        private Long invoiceId;
        private BigDecimal amount;
    }
}
