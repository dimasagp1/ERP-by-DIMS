package id.herbatech.erp.rnd;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "product_spec", schema = "rnd")
public class ProductSpec extends DocumentEntity {
    private Long itemId;
    private String specCategory;
    private String parametersJson;
    private String notes;
}
