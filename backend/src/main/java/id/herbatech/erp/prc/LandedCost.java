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
 * PRC-10 Impor & landed cost: freight, asuransi, bea masuk dialokasikan ke harga pokok lot yang diterima;
 * PPN & PPh 22 impor menjadi kredit pajak.
 */
@Getter
@Setter
@Entity
@Table(name = "landed_cost", schema = "prc")
public class LandedCost extends DocumentEntity {

    private Long poId;
    private String shipmentNo;
    private String blNo;
    private String pibNo;
    private LocalDate pibDate;
    private LocalDate arrivalDate;
    private String allocBasis = "VALUE";
    private String notes;
    private BigDecimal capitalized = BigDecimal.ZERO;
    private BigDecimal taxCredit = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "landedCost", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Charge> charges = new ArrayList<>();

    @OneToMany(mappedBy = "landedCost", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Allocation> allocations = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "lc_charge", schema = "prc")
    public static class Charge {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "lc_id")
        private LandedCost landedCost;

        private short lineNo;
        private String kind;
        private Long partnerId;
        private String reference;
        private BigDecimal amount;
    }

    @Getter
    @Setter
    @Entity
    @Table(name = "lc_alloc", schema = "prc")
    public static class Allocation {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "lc_id")
        private LandedCost landedCost;

        private short lineNo;
        private Long grLineId;
        private Long itemId;
        private Long lotId;
        private BigDecimal qty;
        private BigDecimal baseValue;
        private BigDecimal allocated = BigDecimal.ZERO;
    }
}
