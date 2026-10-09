package id.herbatech.erp.qms;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "stability_study", schema = "qms")
public class StabilityStudy extends DocumentEntity {
    private Long itemId;
    private Long lotId;
    private String studyCondition;
    private String timepoints;
}