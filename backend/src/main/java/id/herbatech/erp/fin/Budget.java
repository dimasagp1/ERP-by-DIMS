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

/** FIN-50 Anggaran OPEX/CAPEX per cost center × akun × bulan. Diposting = berlaku; revisi baru menggantikan yang lama. */
@Getter
@Setter
@Entity
@Table(name = "budget", schema = "fin")
public class Budget extends DocumentEntity {

    private short year;
    private short revision;
    private String kind = "OPEX";
    private String description;
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "budget_line", schema = "fin")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "budget_id")
        private Budget budget;

        private short lineNo;
        private Long costCenterId;
        private Long accountId;
        private BigDecimal m01 = BigDecimal.ZERO, m02 = BigDecimal.ZERO, m03 = BigDecimal.ZERO, m04 = BigDecimal.ZERO,
                m05 = BigDecimal.ZERO, m06 = BigDecimal.ZERO, m07 = BigDecimal.ZERO, m08 = BigDecimal.ZERO,
                m09 = BigDecimal.ZERO, m10 = BigDecimal.ZERO, m11 = BigDecimal.ZERO, m12 = BigDecimal.ZERO;
        private BigDecimal total = BigDecimal.ZERO;

        public List<BigDecimal> months() {
            return List.of(m01, m02, m03, m04, m05, m06, m07, m08, m09, m10, m11, m12);
        }
    }
}
