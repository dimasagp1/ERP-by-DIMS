package id.herbatech.erp.pre;

import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "machine", schema = "pre")
public class Machine extends BaseEntity {
    private String code;
    private String name;
    private Long lineId;
    private String brand;
    private String model;
    private String status;
    private String qualificationStatus;
    private LocalDate lastPmDate;
    private LocalDate nextPmDate;
}
