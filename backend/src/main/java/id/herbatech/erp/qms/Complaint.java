package id.herbatech.erp.qms;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "complaint", schema = "qms")
public class Complaint extends DocumentEntity {
    private Long partnerId;
    private Long lotId;
    private String description;
    private String decision;
}