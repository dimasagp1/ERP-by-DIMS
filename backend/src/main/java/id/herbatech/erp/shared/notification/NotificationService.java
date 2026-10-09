package id.herbatech.erp.shared.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Notifikasi lonceng navbar (PRD §13). Pengiriman email memakai template SYS-15 dan ditambahkan
 * sebagai kanal berikutnya; semua pemicu sudah lewat kelas ini.
 */
@Service
public class NotificationService {

    public record NotificationView(Long id, String kind, String title, String body, String link, Instant readAt,
                                   Instant createdAt) {
    }

    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void notify(Collection<Long> userIds, String kind, String title, String body, String link) {
        List<Long> ids = userIds.stream().filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("INSERT INTO core.notification (user_id, kind, title, body, link) VALUES (?, ?, ?, ?, ?)",
                ids.stream().map(id -> new Object[]{id, kind, title, body, link}).toList());
    }

    public List<NotificationView> recent(Long userId, int limit) {
        return jdbc.query("""
                        SELECT id, kind, title, body, link, read_at, created_at FROM core.notification
                        WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?""",
                (rs, i) -> new NotificationView(rs.getLong("id"), rs.getString("kind"), rs.getString("title"),
                        rs.getString("body"), rs.getString("link"),
                        rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()),
                userId, limit);
    }

    public int unread(Long userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM core.notification WHERE user_id = ? AND read_at IS NULL",
                Integer.class, userId);
        return n == null ? 0 : n;
    }

    public void markRead(Long userId, Long id) {
        jdbc.update("UPDATE core.notification SET read_at = now() WHERE user_id = ? AND id = ? AND read_at IS NULL", userId, id);
    }

    public void markAllRead(Long userId) {
        jdbc.update("UPDATE core.notification SET read_at = now() WHERE user_id = ? AND read_at IS NULL", userId);
    }
}
