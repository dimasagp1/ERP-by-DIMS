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

/** FIN-21 Penerimaan pembayaran customer dengan alokasi pelunasan ke faktur. */
@Getter
@Setter
@Entity
@Table(name = "receipt", schema = "fin")
public class Receipt extends DocumentEntity {

    private Long partnerId;
    private Long bankAccountId;
    private String reference;
    private String description;
    private BigDecimal amount = BigDecimal.ZERO;
    private boolean reconciled;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Allocation> allocations = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "receipt_allocation", schema = "fin")
    public static class Allocation {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "receipt_id")
        private Receipt receipt;

        private short lineNo;
        private Long invoiceId;
        private BigDecimal amount;
    }
}
