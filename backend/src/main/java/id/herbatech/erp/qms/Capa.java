package id.herbatech.erp.qms;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "capa", schema = "qms")
public class Capa extends DocumentEntity {
    private Long deviationId;
    private Long complaintId;
    private Long auditId;
    private Long picId;
    private LocalDate dueDate;
    private String effectiveness;
}