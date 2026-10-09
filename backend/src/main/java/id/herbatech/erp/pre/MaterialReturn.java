package id.herbatech.erp.pre;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * PRE-10 Retur sisa bahan per lot ke gudang; diterima gudang (SCM-23) dan mengurangi biaya bahan batch.
 */
@Getter
@Setter
@Entity
@Table(name = "material_return", schema = "pre")
public class MaterialReturn extends DocumentEntity {

    private Long woId;
    private String notes;
    private Instant receivedAt;
    private Long receivedBy;
    private BigDecimal returnedValue = BigDecimal.ZERO;

    @OneToMany(mappedBy = "materialReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "material_return_line", schema = "pre")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "return_id")
        private MaterialReturn materialReturn;

        private short lineNo;
        private Long itemId;
        private Long lotId;
        private BigDecimal qty;
        private Long locationId;
        private BigDecimal unitCost = BigDecimal.ZERO;
    }
}
