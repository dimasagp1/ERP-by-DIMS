package id.herbatech.erp.pre;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "work_request", schema = "pre")
public class MaintenanceWorkRequest extends DocumentEntity {
    private Long machineId;
    private Long requesterId;
    private String priority;
    private String description;
}
