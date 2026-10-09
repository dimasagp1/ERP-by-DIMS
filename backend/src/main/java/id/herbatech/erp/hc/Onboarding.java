package id.herbatech.erp.hc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** HC-05 Onboarding & offboarding dengan checklist lintas departemen (SYS, GA, HC, atasan). */
@Getter
@Setter
@Entity
@Table(name = "onboarding", schema = "hc")
public class Onboarding extends DocumentEntity {

    private Long employeeId;
    /** ONBOARD / OFFBOARD. */
    private String kind = "ONBOARD";
    private LocalDate effectiveDate;
    private String reason;
    private boolean applied;

    @OneToMany(mappedBy = "onboarding", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq")
    private List<Task> tasks = new ArrayList<>();

    @Getter
    @Setter
    @Entity
    @Table(name = "onboarding_task", schema = "hc")
    public static class Task {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "onboarding_id")
        private Onboarding onboarding;

        private short seq;
        private String code;
        private String label;
        private String ownerApp;
        private boolean done;
        private Long doneBy;
        private Instant doneAt;
        private String note;
    }
}
