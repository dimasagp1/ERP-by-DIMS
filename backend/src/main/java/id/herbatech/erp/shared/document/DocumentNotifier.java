package id.herbatech.erp.shared.document;

import id.herbatech.erp.shared.notification.NotificationService;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

/** Memberi tahu pembuat dokumen saat dokumennya disetujui atau ditolak. */
@Component
public class DocumentNotifier {

    private final NotificationService notifications;

    public DocumentNotifier(NotificationService notifications) {
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    public void on(DocumentStatusChanged e) {
        if (e.createdBy() == null || e.createdBy().equals(e.actorId())) {
            return;
        }
        String link = "/d/" + e.docType() + "/" + e.docId();
        switch (e.toStatus()) {
            case "APPROVED" -> notifications.notify(List.of(e.createdBy()), "DOC_APPROVED",
                    e.docNo() + " disetujui", "Dokumen Anda telah disetujui.", link);
            case "REJECTED" -> notifications.notify(List.of(e.createdBy()), "DOC_REJECTED",
                    e.docNo() + " ditolak", e.reason(), link);
            case "CANCELLED" -> notifications.notify(List.of(e.createdBy()), "DOC_CANCELLED",
                    e.docNo() + " dibatalkan", e.reason(), link);
            default -> {
            }
        }
    }
}
