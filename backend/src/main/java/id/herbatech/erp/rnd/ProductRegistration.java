package id.herbatech.erp.rnd;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "product_registration", schema = "rnd")
public class ProductRegistration extends DocumentEntity {
    private Long itemId;
    private String regType;
    private String registrationNo;
    private String halalNo;
    private LocalDate submissionDate;
    private LocalDate approvalDate;
    private LocalDate expiryDate;
}
