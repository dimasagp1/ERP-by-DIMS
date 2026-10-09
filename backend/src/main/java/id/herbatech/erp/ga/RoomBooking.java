package id.herbatech.erp.ga;

import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "room_booking", schema = "ga")
public class RoomBooking extends BaseEntity {
    private String roomName;
    private Long bookedBy;
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;
    private String agenda;
    private String status;
}
