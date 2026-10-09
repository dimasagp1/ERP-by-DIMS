package id.herbatech.erp.pre;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "line_clearance", schema = "pre")
public class LineClearance extends DocumentEntity {
    private Long lineId;
    private Long woId;
    private Long checkedBy;
    private Long qaInspectorId;
    private Boolean cleanlinessPass;
    private Boolean previousBatchCleared;
    private String statusResult;
}
