package id.herbatech.erp.shared.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/** Kolom standar semua tabel: id, version (optimistic lock), dibuat/diubah oleh & kapan. */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @CreatedBy
    @Column(updatable = false)
    private Long createdBy;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @LastModifiedDate
    private Instant updatedAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @LastModifiedBy
    private Long updatedBy;
}
