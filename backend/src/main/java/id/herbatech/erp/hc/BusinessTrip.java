package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** HC-08 / ESS-09 Surat perjalanan dinas (SPD). */
@Getter
@Setter
@Entity
@Table(name = "business_trip", schema = "hc")
public class BusinessTrip extends DocumentEntity {

    private Long employeeId;
    private String destination;
    private String purpose;
    private LocalDate startDate;
    private LocalDate endDate;
    private String transport = "DARAT";
    private boolean abroad;
    private BigDecimal perDiem = BigDecimal.ZERO;
    private BigDecimal advanceAmount = BigDecimal.ZERO;
    private Long costCenterId;
    private String advanceDocNo;
}
