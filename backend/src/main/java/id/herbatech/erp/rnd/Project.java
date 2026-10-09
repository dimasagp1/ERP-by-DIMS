package id.herbatech.erp.rnd;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "project", schema = "rnd")
public class Project extends DocumentEntity {
    private String name;
    private String category;
    private String stage;
    private Long picId;
    private LocalDate targetLaunchDate;
    private String description;
}
