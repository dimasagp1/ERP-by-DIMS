package id.herbatech.erp.shared.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Integrasi keluar (SYS-13, PRD §17 "log integrasi bisa dicoba ulang"). Modul memasukkan pesan ke antrean
 * {@code sys.integration_log} dalam transaksinya sendiri; pengirim terjadwal mengirim ke endpoint sistem tujuan
 * (BSC, HRIS, Odoo) dan mencatat hasilnya. Gagal dicoba ulang otomatis sampai 5 kali.
 */
@Slf4j
@Service
public class IntegrationService {

    public static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public IntegrationService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Antrekan pesan keluar. {@code system} mis. BSC; endpoint diambil dari pengaturan SYS saat dikirim. */
    public Long enqueue(String system, String endpoint, String refDoc, Object payload) {
        return jdbc.queryForObject("""
                        INSERT INTO sys.integration_log (system, direction, endpoint, ref_doc, status, request_body)
                        VALUES (?, 'OUT', ?, ?, 'PENDING', ?) RETURNING id""", Long.class,
                system, endpoint, refDoc, mapper.writeValueAsString(payload));
    }

    /** Catat pesan masuk (mis. impor mesin absensi). */
    public void logInbound(String system, String endpoint, String refDoc, String status, String summary, String error) {
        jdbc.update("""
                        INSERT INTO sys.integration_log (system, direction, endpoint, ref_doc, status, response_body, error, attempts, last_attempt_at)
                        VALUES (?, 'IN', ?, ?, ?, ?, ?, 1, now())""",
                system, endpoint, refDoc, status, summary, error);
    }

    /** Kirim antrean setiap menit; gagal dicoba ulang dengan jeda bertambah. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void dispatch() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, system, endpoint, request_body, attempts FROM sys.integration_log
                WHERE direction = 'OUT' AND (status = 'PENDING'
                   OR (status = 'FAILED' AND attempts < ? AND last_attempt_at < now() - (attempts * interval '5 minutes')))
                ORDER BY id LIMIT 50""", MAX_ATTEMPTS);
        for (Map<String, Object> r : rows) {
            send(((Number) r.get("id")).longValue(), (String) r.get("system"), (String) r.get("endpoint"),
                    (String) r.get("request_body"));
        }
    }

    private void send(Long id, String system, String endpoint, String body) {
        String base = setting("integration." + system.toLowerCase() + ".url");
        String token = setting("integration." + system.toLowerCase() + ".token");
        if (base == null || base.isBlank()) {
            fail(id, null, "URL " + system + " belum dikonfigurasi (Pengaturan Sistem > Umum: integration."
                    + system.toLowerCase() + ".url)");
            return;
        }
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(base.replaceAll("/+$", "") + endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body));
            if (token != null && !token.isBlank()) {
                req.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                jdbc.update("""
                        UPDATE sys.integration_log SET status = 'SUCCESS', http_status = ?, response_body = ?, error = NULL,
                               attempts = attempts + 1, last_attempt_at = now() WHERE id = ?""",
                        res.statusCode(), truncate(res.body()), id);
            } else {
                fail(id, res.statusCode(), "HTTP " + res.statusCode() + ": " + truncate(res.body()));
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            fail(id, null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void fail(Long id, Integer httpStatus, String error) {
        jdbc.update("""
                UPDATE sys.integration_log SET status = 'FAILED', http_status = ?, error = ?, attempts = attempts + 1,
                       last_attempt_at = now() WHERE id = ?""", httpStatus, truncate(error), id);
    }

    private String setting(String key) {
        return jdbc.queryForList("SELECT value FROM sys.app_setting WHERE app_code = 'SYS' AND key = ?", String.class, key)
                .stream().findFirst().orElse(null);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 4000 ? s : s.substring(0, 4000);
    }
}
