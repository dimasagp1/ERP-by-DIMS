package id.herbatech.erp.shared.notification;

import id.herbatech.erp.shared.security.UserContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    public record NotificationList(int unread, List<NotificationService.NotificationView> items) {
    }

    @GetMapping
    public NotificationList list(@RequestParam(defaultValue = "30") int limit) {
        Long uid = UserContext.userId();
        return new NotificationList(service.unread(uid), service.recent(uid, Math.min(limit, 100)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable Long id) {
        service.markRead(UserContext.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll() {
        service.markAllRead(UserContext.userId());
        return ResponseEntity.noContent().build();
    }
}
