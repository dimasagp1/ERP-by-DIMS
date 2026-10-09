package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "recruitment_request", schema = "hc")
public class RecruitmentRequest extends DocumentEntity {
    private Long positionId;
    private Integer headcountNeeded;
    private LocalDate targetDate;
    private String justification;
}
