package id.herbatech.erp.ga;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "service_request", schema = "ga")
public class GaServiceRequest extends DocumentEntity {
    private String reqType;
    private Long requesterId;
    private String priority;
    private String description;
    private LocalDate slaDueDate;
    private String resolutionNotes;
}
