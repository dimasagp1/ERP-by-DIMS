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
 * SCM-05 Bill of Materials per produk (FG/WIP). Diposting = berlaku; BOM berlaku sebelumnya untuk produk yang sama
 * menjadi Selesai. Di M3 penyusunan formula pindah ke RnD (RND-04) dengan kontrol perubahan.
 */
@Getter
@Setter
@Entity
@Table(name = "bom", schema = "scm")
public class Bom extends DocumentEntity {

    private Long itemId;
    private int revision = 1;
    private BigDecimal baseQty;
    private BigDecimal stdHours = BigDecimal.ZERO;
    private LocalDate effectiveFrom;
    private String notes;

    @OneToMany(mappedBy = "bom", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "bom_line", schema = "scm")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "bom_id")
        private Bom bom;

        private short lineNo;
        private Long componentItemId;
        private BigDecimal qty;
        private BigDecimal scrapPct = BigDecimal.ZERO;
        private String notes;
    }
}
