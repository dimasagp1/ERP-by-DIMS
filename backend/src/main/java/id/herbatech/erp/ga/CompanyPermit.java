package id.herbatech.erp.ga;

import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "company_permit", schema = "ga")
public class CompanyPermit extends BaseEntity {
    private String name;
    private String permitNo;
    private String issuer;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private Integer reminderDays;
    private String status;
}
