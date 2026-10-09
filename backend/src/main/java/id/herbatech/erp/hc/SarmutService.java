package id.herbatech.erp.hc;

import id.herbatech.erp.shared.attachment.AttachmentService;
import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.integration.IntegrationService;
import id.herbatech.erp.shared.meta.KpiProvider;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HC-13 Sasaran Mutu (PRD §16): nilai dihitung dari transaksi modul lewat {@link KpiProvider}, nilai manual lewat
 * dokumen SRM yang disetujui, lalu skor per departemen dikirim ke BSC (SYS-13).
 */
@Service
public class SarmutService {

    /** Skor maksimum per indikator (pencapaian dibatasi 120%). */
    static final BigDecimal CAP = BigDecimal.valueOf(120);

    public record KpiValue(Long kpiId, String code, String name, String appCode, String unit, String direction,
                           BigDecimal weight, String source, BigDecimal target, BigDecimal actual, BigDecimal score,
                           String valueSource, String sourceDoc, Instant calculatedAt, Instant sentToBscAt) {
    }

    public record DeptScore(String appCode, BigDecimal score, int indicators, int filled) {
    }

    public record Summary(String period, List<KpiValue> values, List<DeptScore> departments) {
    }

    private final JdbcTemplate jdbc;
    private final List<KpiProvider> providers;
    private final IntegrationService integration;

    SarmutService(JdbcTemplate jdbc, List<KpiProvider> providers, IntegrationService integration) {
        this.jdbc = jdbc;
        this.providers = providers;
        this.integration = integration;
    }

    static BigDecimal score(String direction, BigDecimal target, BigDecimal actual) {
        if (actual == null) {
            return null;
        }
        BigDecimal s;
        if ("LOWER".equals(direction)) {
            if (target.signum() == 0) {
                s = actual.signum() == 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
            } else if (actual.signum() <= 0) {
                s = CAP;
            } else {
                s = target.multiply(BigDecimal.valueOf(100)).divide(actual, 2, RoundingMode.HALF_UP);
            }
        } else {
            s = target.signum() == 0 ? BigDecimal.valueOf(100)
                    : actual.multiply(BigDecimal.valueOf(100)).divide(target, 2, RoundingMode.HALF_UP);
        }
        return s.min(CAP).max(BigDecimal.ZERO);
    }

    /** Hitung semua indikator otomatis untuk periode & plant. */
    @Transactional
    public Summary calculate(YearMonth period, Long plantId) {
        Map<String, BigDecimal> auto = new HashMap<>();
        providers.forEach(p -> auto.putAll(p.compute(period, plantId)));
        String per = "%d%02d".formatted(period.getYear(), period.getMonthValue());
        for (Map<String, Object> k : jdbc.queryForList("SELECT * FROM hc.sarmut_kpi WHERE active AND source = 'AUTO'")) {
            BigDecimal actual = auto.get((String) k.get("auto_key"));
            if (actual == null) {
                continue;
            }
            BigDecimal target = (BigDecimal) k.get("target");
            upsert(((Number) k.get("id")).longValue(), plantId, per, actual.setScale(4, RoundingMode.HALF_UP), target,
                    score((String) k.get("direction"), target, actual), "AUTO", null, null);
        }
        return summary(period, plantId);
    }

    void upsert(Long kpiId, Long plantId, String period, BigDecimal actual, BigDecimal target, BigDecimal score,
                String source, String sourceDoc, String note) {
        jdbc.update("""
                INSERT INTO hc.sarmut_value (kpi_id, plant_id, period, actual, target, score, source, source_doc, note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (kpi_id, plant_id, period) DO UPDATE SET actual = EXCLUDED.actual, target = EXCLUDED.target,
                    score = EXCLUDED.score, source = EXCLUDED.source, source_doc = EXCLUDED.source_doc, note = EXCLUDED.note,
                    calculated_at = now(), sent_to_bsc_at = NULL""",
                kpiId, plantId, period, actual, target, score, source, sourceDoc, note);
    }

    public Summary summary(YearMonth period, Long plantId) {
        String per = "%d%02d".formatted(period.getYear(), period.getMonthValue());
        List<KpiValue> values = jdbc.query("""
                SELECT k.id, k.code, k.name, k.app_code, k.unit, k.direction, k.weight, k.source, k.target AS kpi_target,
                       v.target, v.actual, v.score, v.source AS vsource, v.source_doc, v.calculated_at, v.sent_to_bsc_at
                FROM hc.sarmut_kpi k LEFT JOIN hc.sarmut_value v ON v.kpi_id = k.id AND v.period = ? AND v.plant_id = ?
                WHERE k.active ORDER BY k.app_code, k.code""",
                (rs, i) -> new KpiValue(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("app_code"),
                        rs.getString("unit"), rs.getString("direction"), rs.getBigDecimal("weight"), rs.getString("source"),
                        rs.getBigDecimal("target") != null ? rs.getBigDecimal("target") : rs.getBigDecimal("kpi_target"),
                        rs.getBigDecimal("actual"), rs.getBigDecimal("score"), rs.getString("vsource"), rs.getString("source_doc"),
                        rs.getTimestamp("calculated_at") == null ? null : rs.getTimestamp("calculated_at").toInstant(),
                        rs.getTimestamp("sent_to_bsc_at") == null ? null : rs.getTimestamp("sent_to_bsc_at").toInstant()),
                per, plantId);
        Map<String, BigDecimal[]> agg = new LinkedHashMap<>();
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (KpiValue v : values) {
            counts.computeIfAbsent(v.appCode(), k -> new int[2])[0]++;
            if (v.score() != null) {
                counts.get(v.appCode())[1]++;
                BigDecimal[] a = agg.computeIfAbsent(v.appCode(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                a[0] = a[0].add(v.score().multiply(v.weight()));
                a[1] = a[1].add(v.weight());
            }
        }
        List<DeptScore> depts = new ArrayList<>();
        counts.forEach((app, c) -> {
            BigDecimal[] a = agg.get(app);
            depts.add(new DeptScore(app, a == null || a[1].signum() == 0 ? null : a[0].divide(a[1], 2, RoundingMode.HALF_UP), c[0], c[1]));
        });
        return new Summary(per, values, depts);
    }

    /** Kirim nilai & skor periode ke BSC lewat antrean integrasi (dicoba ulang otomatis bila gagal). */
    @Transactional
    public Long sendToBsc(YearMonth period, Long plantId) {
        Summary s = summary(period, plantId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("period", s.period());
        payload.put("plant", jdbc.queryForObject("SELECT code FROM sys.plant WHERE id = ?", String.class, plantId));
        payload.put("departments", s.departments());
        payload.put("indicators", s.values().stream().filter(v -> v.actual() != null).map(v -> Map.of(
                "code", v.code(), "department", v.appCode(), "target", v.target(), "actual", v.actual(),
                "score", v.score(), "source", v.valueSource())).toList());
        Long id = integration.enqueue("BSC", "/sarmut", "SARMUT/" + s.period(), payload);
        jdbc.update("UPDATE hc.sarmut_value SET sent_to_bsc_at = now() WHERE period = ? AND plant_id = ?", s.period(), plantId);
        return id;
    }
}

interface SarmutInputRepository extends DocumentRepository<SarmutInput> {
}

@Component
class SarmutInputHandler implements DocumentHandler<SarmutInput> {

    private final SarmutInputRepository repo;
    private final SarmutService sarmut;
    private final AttachmentService attachments;
    private final JdbcTemplate jdbc;

    SarmutInputHandler(SarmutInputRepository repo, SarmutService sarmut, AttachmentService attachments, JdbcTemplate jdbc) {
        this.repo = repo;
        this.sarmut = sarmut;
        this.attachments = attachments;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "SRM"; }
    @Override public String periodModule() { return "HC"; }
    @Override public SarmutInput load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Input SARMUT", id)); }
    @Override public SarmutInput save(SarmutInput doc) { return repo.save(doc); }

    @Override
    public String summary(SarmutInput d) {
        String kpi = jdbc.queryForList("SELECT code || ' · ' || name FROM hc.sarmut_kpi WHERE id = ?", String.class, d.getKpiId())
                .stream().findFirst().orElse("?");
        return "SARMUT %s · %s = %s".formatted(d.getPeriod(), kpi, d.getActual() == null ? "-" : d.getActual().stripTrailingZeros().toPlainString());
    }

    @Override
    public void validateSubmit(SarmutInput d) {
        if (attachments.list("SRM", d.getId()).isEmpty()) {
            throw new BusinessException("SRM_EVIDENCE", "Nilai SARMUT manual wajib melampirkan bukti (PRD HC aturan 6)");
        }
    }

    @Override
    public void onApproved(SarmutInput d) {
        Map<String, Object> k = jdbc.queryForMap("SELECT target, direction FROM hc.sarmut_kpi WHERE id = ?", d.getKpiId());
        BigDecimal target = (BigDecimal) k.get("target");
        sarmut.upsert(d.getKpiId(), d.getPlantId(), d.getPeriod(), d.getActual(), target,
                SarmutService.score((String) k.get("direction"), target, d.getActual()), "MANUAL", d.getDocNo(), d.getEvidence());
    }
}

@RestController
@RequestMapping("/api/hc/sarmut-inputs")
class SarmutInputController extends DocumentApi<SarmutInput> {

    private final JdbcTemplate jdbc;

    SarmutInputController(SarmutInputHandler handler, SarmutInputRepository repo, Support support, JdbcTemplate jdbc) {
        super(handler, repo, support, SarmutInput.class);
        this.jdbc = jdbc;
    }

    @Override
    protected void beforeSave(SarmutInput d, boolean isNew) {
        PayrollService.period(d.getPeriod());
        require(d.getKpiId() != null, "SRM_KPI", "Pilih indikator");
        require(d.getActual() != null, "SRM_ACTUAL", "Nilai aktual wajib diisi");
        require(d.getEvidence() != null && d.getEvidence().trim().length() >= 10, "SRM_EVIDENCE", "Uraikan bukti/sumber data (min. 10 karakter)");
        String source = jdbc.queryForList("SELECT source FROM hc.sarmut_kpi WHERE id = ? AND active", String.class, d.getKpiId())
                .stream().findFirst().orElseThrow(() -> new BusinessException("SRM_KPI", "Indikator tidak ditemukan"));
        require("MANUAL".equals(source), "SRM_AUTO", "Indikator otomatis dihitung dari transaksi, bukan diinput manual");
    }

    @Override
    protected void enrich(Map<String, Object> body, SarmutInput d) {
        body.put("kpiName", jdbc.queryForList("SELECT code || ' · ' || name FROM hc.sarmut_kpi WHERE id = ?", String.class, d.getKpiId())
                .stream().findFirst().orElse(null));
    }
}

@RestController
@RequestMapping("/api/hc/sarmut")
class SarmutController {

    private final SarmutService sarmut;
    private final PermissionService perm;

    SarmutController(SarmutService sarmut, PermissionService perm) {
        this.sarmut = sarmut;
        this.perm = perm;
    }

    @GetMapping
    public SarmutService.Summary get(@RequestParam String period) {
        perm.require("HC-13", Action.VIEW);
        return sarmut.summary(PayrollService.period(period), UserContext.plantId());
    }

    @PostMapping("/calculate")
    public SarmutService.Summary calculate(@RequestParam String period) {
        perm.require("HC-13", Action.EDIT);
        return sarmut.calculate(PayrollService.period(period), UserContext.plantId());
    }

    @PostMapping("/send-bsc")
    public Map<String, Object> send(@RequestParam String period) {
        perm.require("HC-13", Action.POST);
        Long id = sarmut.sendToBsc(PayrollService.period(period), UserContext.plantId());
        return Map.of("integrationLogId", id);
    }
}
