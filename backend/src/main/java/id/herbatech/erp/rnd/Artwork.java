package id.herbatech.erp.rnd;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "artwork", schema = "rnd")
public class Artwork extends DocumentEntity {
    private Long itemId;
    private String artworkCode;
    private String packagingVersion;
    private String fileUrl;
    private Boolean approvalQa;
    private Boolean approvalMarketing;
}
