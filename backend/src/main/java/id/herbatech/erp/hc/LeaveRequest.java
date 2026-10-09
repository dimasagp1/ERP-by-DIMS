package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** HC-08 / ESS-01 Pengajuan cuti & izin. */
@Getter
@Setter
@Entity
@Table(name = "leave_request", schema = "hc")
public class LeaveRequest extends DocumentEntity {

    private Long employeeId;
    private Long leaveTypeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal days = BigDecimal.ZERO;
    private String reason;
    private Long delegateEmployeeId;
}
