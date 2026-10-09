package id.herbatech.erp.ga;

import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "inventory_asset", schema = "ga")
public class InventoryAsset extends BaseEntity {
    private String assetCode;
    private String name;
    private String category;
    private Long employeeId;
    private String location;
    private String condition;
    private String serialNumber;
    private LocalDate purchaseDate;
}
