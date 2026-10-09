package id.herbatech.erp.qms;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "change_control", schema = "qms")
public class ChangeControl extends DocumentEntity {
    private String type;
    private String description;
    private String impact;
    private Integer riskScore;
}