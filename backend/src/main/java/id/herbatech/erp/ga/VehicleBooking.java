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
@Table(name = "vehicle_booking", schema = "ga")
public class VehicleBooking extends DocumentEntity {
    private String vehicleName;
    private String licensePlate;
    private Long requesterId;
    private String driverName;
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;
    private String destination;
    private String purpose;
}
