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

/**
 * FIN-30 Kas kecil & uang muka kerja.
 * EXPENSE = pengeluaran kas kecil; ADVANCE = uang muka ke karyawan (mis. dari SPD); SETTLEMENT = pertanggungjawaban uang muka.
 */
@Getter
@Setter
@Entity
@Table(name = "cash_voucher", schema = "fin")
public class CashVoucher extends DocumentEntity {

    private String kind = "EXPENSE";
    private Long cashAccountId;
    private Long employeeId;
    private Long advanceId;
    private String sourceDocNo;
    private String description;
    private BigDecimal amount = BigDecimal.ZERO;
    private BigDecimal settledAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "cash_voucher_line", schema = "fin")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "voucher_id")
        private CashVoucher voucher;

        private short lineNo;
        private Long accountId;
        private Long costCenterId;
        private String description;
        private BigDecimal amount;
    }
}
