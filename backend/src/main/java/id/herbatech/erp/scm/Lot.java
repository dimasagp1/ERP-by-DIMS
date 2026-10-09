package id.herbatech.erp.scm;

import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** Nomor lot bahan / batch produk. Status QC hanya diubah QA Release Officer (PRD QMS aturan 1). */
@Getter
@Setter
@Entity
@Table(name = "lot", schema = "scm")
public class Lot extends BaseEntity {

    public enum QcStatus { QUARANTINE, RELEASED, REJECTED, HOLD }

    private String lotNo;
    private Long itemId;
    private String supplierLot;
    private LocalDate mfgDate;
    private LocalDate expDate;

    @Enumerated(EnumType.STRING)
    private QcStatus qcStatus = QcStatus.QUARANTINE;

    private String sourceDoc;

    public boolean isExpiredOn(LocalDate date) {
        return expDate != null && expDate.isBefore(date);
    }
}
