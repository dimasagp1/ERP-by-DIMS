package id.herbatech.erp.shared.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Tugas approval satu level untuk satu dokumen (APPROVAL_LOG di ERD). */
@Getter
@Setter
@Entity
@Table(name = "approval_task", schema = "core")
public class ApprovalTask {

    public enum Status { WAITING, PENDING, APPROVED, REJECTED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String docType;
    private Long docId;
    private String docNo;
    private String docSummary;
    private BigDecimal docAmount;
    private String appCode;
    private Long plantId;
    private short level;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Long assigneeUserId;
    private String assigneeRole;
    private String assigneeApp;
    private String assigneeLabel;
    private Long requestedBy;
    private Instant activatedAt;
    private Long decidedBy;
    private Instant decidedAt;
    private String decisionReason;
    private Instant remindedAt;
    private Instant escalatedAt;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}
