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
@Table(name = "trial", schema = "rnd")
public class Trial extends DocumentEntity {
    private Long projectId;
    private Long formulaId;
    private String trialType;
    private LocalDate trialDate;
    private String parametersJson;
    private String resultSummary;
    private String decision;
}
