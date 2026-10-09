package id.herbatech.erp.qms;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "deviation", schema = "qms")
public class Deviation extends DocumentEntity {
    private String sourceType;
    private Long sourceId;
    private String deviationClass;
    private String rootCause;
    private String lotIds;
}