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
 * SCM-25 Transfer antar gudang/plant per lot & lokasi.
 */
@Getter
@Setter
@Entity
@Table(name = "transfer", schema = "scm")
public class StockTransfer extends DocumentEntity {

    private String notes;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "transfer_line", schema = "scm")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "transfer_id")
        private StockTransfer transfer;

        private short lineNo;
        private Long itemId;
        private Long lotId;
        private Long fromLocationId;
        private Long toLocationId;
        private BigDecimal qty;
    }
}
