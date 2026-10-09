package id.herbatech.erp.shared.meta;

import id.herbatech.erp.shared.approval.ApprovalService;
import id.herbatech.erp.shared.domain.DocStatus;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.CurrentUser;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import id.herbatech.erp.shared.security.ViewScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Data launcher (Perlu tindakan Anda, badge) dan dashboard per aplikasi (KPI + antrean kerja). */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    public record QueueItem(String docType, Long docId, String docNo, String menuCode, String summary, String status,
                            String info, BigDecimal amount, Instant since, Long taskId) {
    }

    public record AppDashboard(String app, List<DashboardProvider.Kpi> kpis, boolean kpiAvailable, List<QueueItem> queue) {
    }

    public record Launcher(int approvalCount, List<QueueItem> tasks, Map<String, Integer> badges, List<QueueItem> myDocuments) {
    }

    private final ApprovalService approvals;
    private final List<DashboardProvider> providers;
    private final PermissionService perm;
    private final JdbcTemplate jdbc;

    public DashboardController(ApprovalService approvals, List<DashboardProvider> providers, PermissionService perm,
                               JdbcTemplate jdbc) {
        this.approvals = approvals;
        this.providers = providers;
        this.perm = perm;
        this.jdbc = jdbc;
    }

    @GetMapping("/launcher")
    public Launcher launcher() {
        List<ApprovalService.TaskView> inbox = approvals.inbox(null);
        Map<String, Integer> badges = inbox.stream()
                .collect(Collectors.groupingBy(ApprovalService.TaskView::appCode, LinkedHashMap::new, Collectors.summingInt(t -> 1)));
        List<QueueItem> tasks = inbox.stream().limit(8).map(DashboardController::fromTask).toList();
        return new Launcher(inbox.size(), tasks, badges, myDocuments(null, 8));
    }

    @GetMapping("/{app}")
    public AppDashboard app(@PathVariable String app) {
        CurrentUser me = UserContext.current();
        List<DashboardProvider.Kpi> kpis = providers.stream()
                .filter(p -> p.appCode().equalsIgnoreCase(app))
                .flatMap(p -> p.kpis(me.plantId()).stream()).toList();

        List<QueueItem> queue = new ArrayList<>(approvals.inbox(app).stream().map(DashboardController::fromTask).toList());
        queue.addAll(myDocuments(app, 10));
        ViewScope scope = perm.viewScope(app);
        if (scope.ordinal() >= ViewScope.DEPARTMENT.ordinal()) {
            List<Long> seen = queue.stream().map(QueueItem::docId).toList();
            jdbc.query("""
                            SELECT doc_type, doc_id, doc_no, menu_code, summary, status, amount, updated_at
                            FROM core.document_index WHERE app_code = ? AND plant_id = ? AND status IN ('SUBMITTED','APPROVED')
                            ORDER BY updated_at DESC LIMIT 10""",
                    rs -> {
                        if (!seen.contains(rs.getLong("doc_id")) && perm.has(rs.getString("menu_code"), Action.VIEW)) {
                            queue.add(new QueueItem(rs.getString("doc_type"), rs.getLong("doc_id"), rs.getString("doc_no"),
                                    rs.getString("menu_code"), rs.getString("summary"), rs.getString("status"),
                                    "Dokumen departemen", rs.getBigDecimal("amount"), rs.getTimestamp("updated_at").toInstant(), null));
                        }
                    }, app, me.plantId());
        }
        return new AppDashboard(app, kpis, !kpis.isEmpty(), queue);
    }

    private List<QueueItem> myDocuments(String app, int limit) {
        CurrentUser me = UserContext.current();
        List<Object> args = new ArrayList<>(List.of(me.id()));
        String appFilter = "";
        if (app != null) {
            appFilter = " AND app_code = ?";
            args.add(app);
        }
        args.add(limit);
        return jdbc.query("""
                        SELECT doc_type, doc_id, doc_no, menu_code, summary, status, amount, updated_at
                        FROM core.document_index
                        WHERE created_by = ? AND status IN ('DRAFT','SUBMITTED','REJECTED','APPROVED')""" + appFilter
                        + " ORDER BY updated_at DESC LIMIT ?",
                (rs, i) -> new QueueItem(rs.getString("doc_type"), rs.getLong("doc_id"), rs.getString("doc_no"),
                        rs.getString("menu_code"), rs.getString("summary"), rs.getString("status"),
                        infoFor(rs.getString("status")), rs.getBigDecimal("amount"), rs.getTimestamp("updated_at").toInstant(), null),
                args.toArray());
    }

    private static String infoFor(String status) {
        return switch (DocStatus.valueOf(status)) {
            case DRAFT -> "Belum diajukan";
            case SUBMITTED -> "Menunggu approval";
            case REJECTED -> "Perlu diperbaiki";
            case APPROVED -> "Siap diposting";
            default -> "";
        };
    }

    private static QueueItem fromTask(ApprovalService.TaskView t) {
        return new QueueItem(t.docType(), t.docId(), t.docNo(), t.menuCode(), t.docSummary(), "SUBMITTED",
                "Approval level " + t.level() + (t.requestedByName() == null ? "" : " · dari " + t.requestedByName()),
                t.docAmount(), t.activatedAt(), t.id());
    }
}
