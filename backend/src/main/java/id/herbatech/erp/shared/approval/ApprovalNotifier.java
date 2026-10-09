package id.herbatech.erp.shared.approval;

import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.notification.NotificationService;
import id.herbatech.erp.shared.period.BusinessCalendar;
import id.herbatech.erp.shared.security.UserDirectory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Notifikasi approval: pemberitahuan saat level aktif, pengingat > 2 hari kerja,
 * eskalasi ke atasan approver > 4 hari kerja (PRD §13).
 */
@Slf4j
@Component
public class ApprovalNotifier {

    static final int REMIND_AFTER = 2;
    static final int ESCALATE_AFTER = 4;

    private final ApprovalTaskRepository tasks;
    private final NotificationService notifications;
    private final UserDirectory users;
    private final OrgDirectory orgDirectory;
    private final BusinessCalendar calendar;
    private final TimeService time;

    public ApprovalNotifier(ApprovalTaskRepository tasks, NotificationService notifications, UserDirectory users,
                            OrgDirectory org, BusinessCalendar calendar, TimeService time) {
        this.tasks = tasks;
        this.notifications = notifications;
        this.users = users;
        this.orgDirectory = org;
        this.calendar = calendar;
        this.time = time;
    }

    @ApplicationModuleListener
    public void on(ApprovalEvents.ApprovalTaskActivated event) {
        tasks.findById(event.taskId()).ifPresent(t -> notifications.notify(approvers(t), "APPROVAL",
                "Menunggu approval: " + t.getDocNo(),
                (t.getDocSummary() == null ? t.getDocNo() : t.getDocSummary()) + " · level " + t.getLevel(),
                "/approval"));
    }

    /** Setiap hari kerja pukul 07.00 WIB. */
    @Scheduled(cron = "0 0 7 * * MON-FRI", zone = "Asia/Jakarta")
    @Transactional
    public void remindAndEscalate() {
        LocalDate today = time.today();
        for (ApprovalTask t : tasks.findByStatus(ApprovalTask.Status.PENDING)) {
            if (t.getActivatedAt() == null) {
                continue;
            }
            int age = calendar.workingDaysBetween(t.getActivatedAt().atZone(time.zone()).toLocalDate(), today);
            if (age > ESCALATE_AFTER && t.getEscalatedAt() == null) {
                List<Long> superiors = new ArrayList<>();
                for (Long approver : approvers(t)) {
                    Long s = orgDirectory.superiorUserId(approver, today);
                    if (s != null) {
                        superiors.add(s);
                    }
                }
                notifications.notify(superiors, "ESCALATION", "Eskalasi approval: " + t.getDocNo(),
                        t.getDocNo() + " belum diputuskan " + age + " hari kerja oleh " + t.getAssigneeLabel(), "/approval");
                t.setEscalatedAt(time.now());
            } else if (age > REMIND_AFTER && t.getRemindedAt() == null) {
                notifications.notify(approvers(t), "REMINDER", "Pengingat approval: " + t.getDocNo(),
                        t.getDocNo() + " menunggu keputusan Anda sejak " + age + " hari kerja", "/approval");
                t.setRemindedAt(time.now());
            }
        }
    }

    private List<Long> approvers(ApprovalTask t) {
        List<Long> ids = t.getAssigneeUserId() != null
                ? List.of(t.getAssigneeUserId())
                : users.usersWithRole(t.getAssigneeRole(), t.getAssigneeApp(), t.getPlantId());
        return ids.stream().filter(id -> !id.equals(t.getRequestedBy())).toList();
    }
}
