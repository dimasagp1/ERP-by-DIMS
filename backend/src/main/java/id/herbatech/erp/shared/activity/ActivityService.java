package id.herbatech.erp.shared.activity;

import id.herbatech.erp.shared.security.CurrentUser;
import id.herbatech.erp.shared.security.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Mencatat dan membaca riwayat aktivitas dokumen. */
@Service
public class ActivityService {

    public record ActivityView(Long id, String kind, String fromStatus, String toStatus, String message,
                               Long userId, String username, String fullName, Instant ts) {
    }

    private final JdbcTemplate jdbc;

    public ActivityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void status(String docType, Long docId, String from, String to, String message) {
        insert(docType, docId, "STATUS", from, to, message);
    }

    public void log(String docType, Long docId, String kind, String message) {
        insert(docType, docId, kind, null, null, message);
    }

    public List<ActivityView> list(String docType, Long docId) {
        return jdbc.query("""
                        SELECT a.id, a.kind, a.from_status, a.to_status, a.message, a.user_id, a.username, u.full_name, a.ts
                        FROM core.activity_log a LEFT JOIN sys.app_user u ON u.id = a.user_id
                        WHERE a.doc_type = ? AND a.doc_id = ? ORDER BY a.ts, a.id""",
                (rs, i) -> new ActivityView(rs.getLong("id"), rs.getString("kind"), rs.getString("from_status"),
                        rs.getString("to_status"), rs.getString("message"), (Long) rs.getObject("user_id"),
                        rs.getString("username"), rs.getString("full_name"), rs.getTimestamp("ts").toInstant()),
                docType, docId);
    }

    private void insert(String docType, Long docId, String kind, String from, String to, String message) {
        CurrentUser u = UserContext.currentOptional().orElse(null);
        jdbc.update("""
                        INSERT INTO core.activity_log (doc_type, doc_id, kind, from_status, to_status, message, user_id, username)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                docType, docId, kind, from, to, message, u == null ? null : u.id(), u == null ? "system" : u.username());
    }
}
