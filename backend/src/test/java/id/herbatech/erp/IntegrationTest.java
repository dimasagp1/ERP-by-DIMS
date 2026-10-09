package id.herbatech.erp;

import id.herbatech.erp.shared.security.CurrentUser;
import id.herbatech.erp.shared.security.ErpAuthentication;
import id.herbatech.erp.shared.security.UserGrantLoader;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Basis test integrasi: PostgreSQL asli (Testcontainers), migrasi Flyway, pengguna demo. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TransactionTemplate tx;

    @Autowired
    private UserGrantLoader grants;

    /** Menjalankan aksi sebagai pengguna demo tertentu dalam satu transaksi. */
    protected <T> T as(String username, Supplier<T> action) {
        login(username);
        return tx.execute(status -> action.get());
    }

    protected void asVoid(String username, Runnable action) {
        as(username, () -> {
            action.run();
            return null;
        });
    }

    protected void login(String username) {
        Long id = jdbc.queryForObject("SELECT id FROM sys.app_user WHERE username = ?", Long.class, username);
        CurrentUser user = grants.load(id);
        SecurityContextHolder.getContext().setAuthentication(new ErpAuthentication(user, null));
    }

    protected Long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Autowired
    private id.herbatech.erp.shared.approval.ApprovalService approvalService;

    /** Setujui level approval yang sedang menunggu untuk dokumen ini sebagai pengguna tertentu. */
    protected void approveAs(String user, String docType, Long docId) {
        asVoid(user, () -> {
            Long task = approvalService.inbox(null).stream()
                    .filter(t -> t.docType().equals(docType) && t.docId().equals(docId))
                    .findFirst().orElseThrow(() -> new AssertionError(user + " tidak punya tugas approval " + docType + " #" + docId))
                    .id();
            approvalService.approve(task, null, null);
        });
    }

    protected String status(String table, Long id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
    }

    protected java.time.LocalDate today() {
        return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta"));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }
}
