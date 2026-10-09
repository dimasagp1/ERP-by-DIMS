package id.herbatech.erp.pre;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "maintenance_order", schema = "pre")
public class MaintenanceOrder extends DocumentEntity {
    private Long requestId;
    private Long machineId;
    private String maintenanceType;
    private Long technicianId;
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;
    private String causeAnalysis;
    private String sparepartUsedJson;
    private BigDecimal costAmount;
}
