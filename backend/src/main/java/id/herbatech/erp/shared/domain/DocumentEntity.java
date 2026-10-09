package id.herbatech.erp.shared.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Kolom standar semua tabel transaksi (PRD §14): doc_no, plant_id, status, approved_by, approved_at, posted_at.
 * Status hanya boleh diubah oleh {@code DocumentWorkflowService}.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class DocumentEntity extends BaseEntity {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(nullable = false, updatable = false)
    private String docNo;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(nullable = false, updatable = false)
    private Long plantId;

    @Column(nullable = false)
    private LocalDate docDate;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocStatus status = DocStatus.DRAFT;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant submittedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long approvedBy;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant approvedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long postedBy;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant postedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long cancelledBy;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant cancelledAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String cancelReason;
}
