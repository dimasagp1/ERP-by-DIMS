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
 * SCM-46 Pemusnahan barang: usulan, approval QA & FIN, berita acara bertanda tangan elektronik.
 */
@Getter
@Setter
@Entity
@Table(name = "scrap", schema = "scm")
public class Scrap extends DocumentEntity {

    private String reason;
    private String method;
    private String witnesses;
    private Long vendorPartnerId;
    private BigDecimal totalValue = BigDecimal.ZERO;

    @OneToMany(mappedBy = "scrap", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "scrap_line", schema = "scm")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "scrap_id")
        private Scrap scrap;

        private short lineNo;
        private Long itemId;
        private Long lotId;
        private Long locationId;
        private BigDecimal qty;
        private BigDecimal unitCost = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
    }
}
