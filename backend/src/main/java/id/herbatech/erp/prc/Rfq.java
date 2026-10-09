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
 * PRC-05 Permintaan penawaran ke beberapa supplier; PRC-06 perbandingan & pemilihan pemenang membentuk PO.
 */
@Getter
@Setter
@Entity
@Table(name = "rfq", schema = "prc")
public class Rfq extends DocumentEntity {

    private Long prId;
    private LocalDate dueDate;
    private String notes;
    private Long awardedPartnerId;
    private String awardReason;
    private Long poId;

    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Supplier> suppliers = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "rfq_line", schema = "prc")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "rfq_id")
        private Rfq rfq;

        private short lineNo;
        private Long prLineId;
        private Long itemId;
        private String description;
        private BigDecimal qty;
    }

    @Getter
    @Setter
    @Entity
    @Table(name = "rfq_supplier", schema = "prc")
    public static class Supplier {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "rfq_id")
        private Rfq rfq;

        private short lineNo;
        private Long partnerId;
    }
}
