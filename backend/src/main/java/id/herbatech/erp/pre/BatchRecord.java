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
@Table(name = "batch_record", schema = "pre")
public class BatchRecord extends DocumentEntity {
    private Long woId;
    private String batchNo;
    private String stageName;
    private Long operatorId;
    private Long checkerId;
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;
    private String notes;
    private Boolean esignVerified;
}
