package id.herbatech.erp.qms;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.ZonedDateTime;

import id.herbatech.erp.shared.domain.BaseEntity;

@Getter
@Setter
@Entity
@Table(name = "coa", schema = "qms")
public class Coa extends BaseEntity {
    private Long lotId;
    private ZonedDateTime issuedAt;
    private String signedBy;
}