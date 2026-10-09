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
 * PRC-12 Berita acara serah terima jasa: peminta mengonfirmasi jasa selesai; beban diakui ke cost center PO.
 */
@Getter
@Setter
@Entity
@Table(name = "bast", schema = "prc")
public class ServiceAcceptance extends DocumentEntity {

    private Long poId;
    private LocalDate serviceFrom;
    private LocalDate serviceTo;
    private String notes;
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "acceptance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "bast_line", schema = "prc")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "bast_id")
        private ServiceAcceptance acceptance;

        private short lineNo;
        private Long poLineId;
        private BigDecimal qty;
        private BigDecimal amount = BigDecimal.ZERO;
        private String acceptanceNote;
    }
}
