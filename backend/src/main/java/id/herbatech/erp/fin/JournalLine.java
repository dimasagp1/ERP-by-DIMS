package id.herbatech.erp.fin;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "journal_line", schema = "fin")
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id")
    private JournalEntry entry;

    private short lineNo;
    private Long accountId;
    private Long costCenterId;
    private String description;
    private BigDecimal debit = BigDecimal.ZERO;
    private BigDecimal credit = BigDecimal.ZERO;

    public static JournalLine of(Long accountId, Long costCenterId, String description, BigDecimal debit, BigDecimal credit) {
        JournalLine l = new JournalLine();
        l.setAccountId(accountId);
        l.setCostCenterId(costCenterId);
        l.setDescription(description);
        l.setDebit(debit == null ? BigDecimal.ZERO : debit);
        l.setCredit(credit == null ? BigDecimal.ZERO : credit);
        return l;
    }
}
