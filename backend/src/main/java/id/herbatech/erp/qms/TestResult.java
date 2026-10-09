package id.herbatech.erp.qms;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import id.herbatech.erp.shared.domain.BaseEntity;

@Getter
@Setter
@Entity
@Table(name = "test_result", schema = "qms")
public class TestResult extends BaseEntity {
    private Long sampleId;
    private Long specParamId;
    private String value;
    private Boolean pass;
}