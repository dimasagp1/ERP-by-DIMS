package id.herbatech.erp.ga;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "gate_pass", schema = "ga")
public class GatePass extends DocumentEntity {
    private String passType;
    private String personName;
    private String companyName;
    private String vehiclePlate;
    private String goodsDescription;
    private String deliveryDocNo;
    private ZonedDateTime entryTime;
    private ZonedDateTime exitTime;
}
