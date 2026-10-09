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
@Table(name = "disciplinary_action", schema = "hc")
public class DisciplinaryAction extends DocumentEntity {
    private Long employeeId;
    private String actionLevel;
    private LocalDate incidentDate;
    private String violationClause;
    private String description;
    private LocalDate validUntil;
}
