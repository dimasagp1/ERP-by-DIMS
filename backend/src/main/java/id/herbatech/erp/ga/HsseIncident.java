package id.herbatech.erp.ga;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "hsse_incident", schema = "ga")
public class HsseIncident extends DocumentEntity {
    private ZonedDateTime incidentDate;
    private String location;
    private String severity;
    private String description;
    private String impactProductLot;
    private String correctiveAction;
}
