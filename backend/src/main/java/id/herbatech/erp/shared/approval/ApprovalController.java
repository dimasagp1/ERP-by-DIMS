package id.herbatech.erp.shared.approval;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ESS-10 Kotak Approval Saya + keputusan approve/reject. */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    public record Decision(String reason, String password) {
    }

    @GetMapping("/inbox")
    public List<ApprovalService.TaskView> inbox(@RequestParam(required = false) String app) {
        return service.inbox(app);
    }

    @PostMapping("/{taskId}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long taskId, @RequestBody(required = false) Decision d) {
        service.approve(taskId, d == null ? null : d.reason(), d == null ? null : d.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{taskId}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long taskId, @RequestBody Decision d) {
        service.reject(taskId, d.reason(), d.password());
        return ResponseEntity.noContent().build();
    }
}
