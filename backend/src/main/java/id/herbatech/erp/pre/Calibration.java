package id.herbatech.erp.pre;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "calibration", schema = "pre")
public class Calibration extends DocumentEntity {
    private Long machineId;
    private String certNo;
    private LocalDate calibrationDate;
    private LocalDate nextDueDate;
    private Boolean passed;
    private String certFileUrl;
}
