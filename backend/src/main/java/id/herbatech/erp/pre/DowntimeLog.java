package id.herbatech.erp.pre;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "downtime_log", schema = "pre")
public class DowntimeLog extends DocumentEntity {
    private Long lineId;
    private Long woId;
    private String machineName;
    private String reasonCategory;
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;
    private Integer durationMin;
    private String actionTaken;
}
