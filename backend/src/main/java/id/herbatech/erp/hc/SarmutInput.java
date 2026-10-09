package id.herbatech.erp.hc;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** HC-13 Input nilai SARMUT manual: wajib bukti (lampiran + uraian) dan approval atasan (PRD HC aturan 6). */
@Getter
@Setter
@Entity
@Table(name = "sarmut_input", schema = "hc")
public class SarmutInput extends DocumentEntity {

    private String period;
    private Long kpiId;
    private BigDecimal actual;
    private String evidence;
}
