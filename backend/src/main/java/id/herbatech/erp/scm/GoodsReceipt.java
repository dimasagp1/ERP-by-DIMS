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
 * SCM-20 Penerimaan barang dari PO: lot internal + lot supplier & kedaluwarsa, masuk karantina (lot dilacak),
 * jurnal persediaan vs hutang belum difakturkan (GRNI).
 */
@Getter
@Setter
@Entity
@Table(name = "gr", schema = "scm")
public class GoodsReceipt extends DocumentEntity {

    private Long partnerId;
    private Long poId;
    private String deliveryNoteNo;
    private String vehicleNo;
    private String notes;
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "gr_line", schema = "scm")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "gr_id")
        private GoodsReceipt receipt;

        private short lineNo;
        private Long poLineId;
        private Long itemId;
        private BigDecimal qty;
        private String supplierLot;
        private String manufacturer;
        private LocalDate mfgDate;
        private LocalDate expDate;
        private Long locationId;
        private Long lotId;
        private BigDecimal unitCost = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
    }
}
