package id.herbatech.erp.qms;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "batch_release", schema = "qms")
public class BatchRelease extends DocumentEntity {
    private Long lotId;
    private Long woId;
    private String decision;
    private String reviewedBy;
    private ZonedDateTime releasedAt;
}