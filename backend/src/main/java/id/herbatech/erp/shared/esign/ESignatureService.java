package id.herbatech.erp.shared.esign;

import id.herbatech.erp.shared.activity.ActivityService;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.security.CurrentUser;
import id.herbatech.erp.shared.security.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Tanda tangan elektronik (PRD §13, 21 CFR Part 11 / Annex 11): pengguna mengetik ulang kata sandi,
 * sistem mencatat siapa, makna tanda tangan, dan waktu server. Catatan tidak bisa diubah atau dihapus.
 */
@Service
public class ESignatureService {

    /** Makna tanda tangan. */
    public enum Meaning { AUTHOR, VERIFY, REVIEW, APPROVE, RELEASE }

    public record SignatureView(Long id, String meaning, Long userId, String username, String fullName, String reason,
                                Instant signedAt) {
    }

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final ActivityService activity;

    public ESignatureService(JdbcTemplate jdbc, PasswordEncoder encoder, ActivityService activity) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.activity = activity;
    }

    public void sign(String docType, Long docId, Meaning meaning, String password, String reason) {
        CurrentUser u = UserContext.current();
        if (password == null || password.isBlank()) {
            throw new BusinessException("ESIGN_REQUIRED", "Tanda tangan elektronik: masukkan ulang kata sandi Anda");
        }
        String hash = jdbc.queryForObject("SELECT password_hash FROM sys.app_user WHERE id = ?", String.class, u.id());
        if (!encoder.matches(password, hash)) {
            throw new BusinessException("ESIGN_INVALID", "Kata sandi salah, tanda tangan elektronik ditolak");
        }
        jdbc.update("""
                        INSERT INTO core.e_signature (doc_type, doc_id, meaning, user_id, username, full_name, reason)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""",
                docType, docId, meaning.name(), u.id(), u.username(), u.fullName(), reason);
        activity.log(docType, docId, "SIGNATURE", "Ditandatangani secara elektronik (" + meaning.name() + ")");
    }

    public List<SignatureView> list(String docType, Long docId) {
        return jdbc.query("""
                        SELECT id, meaning, user_id, username, full_name, reason, signed_at FROM core.e_signature
                        WHERE doc_type = ? AND doc_id = ? ORDER BY signed_at""",
                (rs, i) -> new SignatureView(rs.getLong("id"), rs.getString("meaning"), rs.getLong("user_id"),
                        rs.getString("username"), rs.getString("full_name"), rs.getString("reason"),
                        rs.getTimestamp("signed_at").toInstant()),
                docType, docId);
    }
}
