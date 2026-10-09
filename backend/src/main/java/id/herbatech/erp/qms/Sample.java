package id.herbatech.erp.qms;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

import id.herbatech.erp.shared.domain.BaseEntity;

@Getter
@Setter
@Entity
@Table(name = "sample", schema = "qms")
public class Sample extends BaseEntity {
    private Long lotId;
    private String source;
    private BigDecimal qty;
    private String sampledBy;
}