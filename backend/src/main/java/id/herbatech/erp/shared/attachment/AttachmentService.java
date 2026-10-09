package id.herbatech.erp.shared.attachment;

import id.herbatech.erp.shared.activity.ActivityService;
import id.herbatech.erp.shared.config.ErpProperties;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.security.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lampiran dokumen (PRD §13). File disimpan di disk dengan nama acak + checksum SHA-256;
 * metadata di {@code core.attachment}. Penyimpanan bisa diganti ke S3/MinIO tanpa mengubah API.
 */
@Service
public class AttachmentService {

    private static final Set<String> BLOCKED_EXT = Set.of("exe", "bat", "cmd", "sh", "js", "msi", "ps1", "dll", "com", "scr");

    public record AttachmentView(Long id, String filename, String contentType, long sizeBytes, String sha256,
                                 Long uploadedBy, String uploadedByName, Instant uploadedAt) {
    }

    public record StoredFile(Path path, String filename, String contentType) {
    }

    private final JdbcTemplate jdbc;
    private final Path root;
    private final ActivityService activity;

    public AttachmentService(JdbcTemplate jdbc, ErpProperties props, ActivityService activity) throws IOException {
        this.jdbc = jdbc;
        this.root = Path.of(props.files().dir()).toAbsolutePath().normalize();
        this.activity = activity;
        Files.createDirectories(root);
    }

    @Transactional
    public AttachmentView store(String docType, Long docId, MultipartFile file) {
        String name = sanitize(file.getOriginalFilename());
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (BLOCKED_EXT.contains(ext)) {
            throw new BusinessException("ATTACHMENT", "Jenis file ." + ext + " tidak diizinkan");
        }
        LocalDate today = LocalDate.now();
        String key = "%d/%02d/%s".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID());
        Path target = root.resolve(key).normalize();
        String sha;
        try {
            Files.createDirectories(target.getParent());
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(file.getInputStream(), md)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            sha = HexFormat.of().formatHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BusinessException("ATTACHMENT", "Gagal menyimpan lampiran: " + e.getMessage());
        }
        Long uid = UserContext.userId();
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO core.attachment (doc_type, doc_id, filename, content_type, size_bytes, storage_key, sha256, uploaded_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, docType);
            ps.setLong(2, docId);
            ps.setString(3, name);
            ps.setString(4, file.getContentType());
            ps.setLong(5, file.getSize());
            ps.setString(6, key);
            ps.setString(7, sha);
            ps.setLong(8, uid);
            return ps;
        }, kh);
        activity.log(docType, docId, "ATTACHMENT", "Melampirkan " + name);
        Long id = ((Number) kh.getKeys().get("id")).longValue();
        return list(docType, docId).stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    }

    public List<AttachmentView> list(String docType, Long docId) {
        return jdbc.query("""
                        SELECT a.id, a.filename, a.content_type, a.size_bytes, a.sha256, a.uploaded_by, u.full_name, a.uploaded_at
                        FROM core.attachment a LEFT JOIN sys.app_user u ON u.id = a.uploaded_by
                        WHERE a.doc_type = ? AND a.doc_id = ? ORDER BY a.uploaded_at""",
                (rs, i) -> new AttachmentView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getString(5), rs.getLong(6), rs.getString(7), rs.getTimestamp(8).toInstant()),
                docType, docId);
    }

    public StoredFile load(String docType, Long docId, Long id) {
        return jdbc.query("SELECT filename, content_type, storage_key FROM core.attachment WHERE id = ? AND doc_type = ? AND doc_id = ?",
                        (rs, i) -> new StoredFile(root.resolve(rs.getString(3)).normalize(), rs.getString(1), rs.getString(2)),
                        id, docType, docId)
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Lampiran", id));
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "lampiran";
        }
        String base = Path.of(name).getFileName().toString().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }
}
