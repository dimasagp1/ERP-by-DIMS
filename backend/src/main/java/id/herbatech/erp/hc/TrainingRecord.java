package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "training_record", schema = "hc")
public class TrainingRecord extends DocumentEntity {
    private String topic;
    private String trainer;
    private LocalDate trainingDate;
    private BigDecimal durationHours;
    private Long qualificationId;
    private String attendeesSummary;
}
