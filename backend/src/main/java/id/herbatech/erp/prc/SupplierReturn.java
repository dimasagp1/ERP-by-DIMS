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
import java.util.ArrayList;
import java.util.List;

/**
 * PRC-11 / SCM-28 Retur & klaim supplier: barang reject keluar dari gudang, nota debet atau penggantian barang.
 */
@Getter
@Setter
@Entity
@Table(name = "supplier_return", schema = "prc")
public class SupplierReturn extends DocumentEntity {

    private Long partnerId;
    private Long poId;
    private String reason;
    private String claimType = "REPLACE";
    private String debitNoteNo;
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "supplierReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "supplier_return_line", schema = "prc")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "return_id")
        private SupplierReturn supplierReturn;

        private short lineNo;
        private Long poLineId;
        private Long itemId;
        private Long lotId;
        private Long locationId;
        private BigDecimal qty;
        private BigDecimal unitCost = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
    }
}
