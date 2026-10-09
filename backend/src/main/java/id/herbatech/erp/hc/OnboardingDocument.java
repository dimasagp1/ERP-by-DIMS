package id.herbatech.erp.hc;

import id.herbatech.erp.fin.CashAdvanceService;
import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import id.herbatech.erp.shared.security.UserGrantLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;

interface OnboardingRepository extends DocumentRepository<Onboarding> {
}

@Slf4j
@Component
class OnboardingHandler implements DocumentHandler<Onboarding> {

    static final List<String[]> ONBOARD = List.of(
            new String[]{"AKUN_SYS", "Buat akun ERP & hak akses (SYS-03)", "SYS"},
            new String[]{"ASET_GA", "Serahkan aset kerja: laptop, ID card, seragam (GA-02)", "GA"},
            new String[]{"KONTRAK", "Kontrak kerja ditandatangani (HC-06)", "HC"},
            new String[]{"BPJS", "Daftarkan BPJS Kesehatan & Ketenagakerjaan", "HC"},
            new String[]{"TRAINING", "Jadwalkan training wajib posisi (HC-11)", "HC"},
            new String[]{"GAJI", "Isi data gaji & rekening (HC-09)", "HC"});
    static final List<String[]> OFFBOARD = List.of(
            new String[]{"SERAH_TERIMA", "Serah terima pekerjaan ke atasan", "HC"},
            new String[]{"ASET_GA", "Pengembalian aset perusahaan (GA-02)", "GA"},
            new String[]{"PINJAMAN", "Pelunasan pinjaman & uang muka", "FIN"},
            new String[]{"EXIT_INTERVIEW", "Exit interview", "HC"},
            new String[]{"AKUN_SYS", "Nonaktifkan akun ERP pada tanggal efektif (otomatis)", "SYS"});

    private final OnboardingRepository repo;
    private final CashAdvanceService advances;
    private final UserGrantLoader grants;
    private final HcSupport hc;
    private final TimeService time;
    private final JdbcTemplate jdbc;

    OnboardingHandler(OnboardingRepository repo, CashAdvanceService advances, UserGrantLoader grants, HcSupport hc,
                      TimeService time, JdbcTemplate jdbc) {
        this.repo = repo;
        this.advances = advances;
        this.grants = grants;
        this.hc = hc;
        this.time = time;
        this.jdbc = jdbc;
    }

    @Override public String docType() { return "OB"; }
    @Override public String periodModule() { return "HC"; }
    @Override public Onboarding load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("On/offboarding", id)); }
    @Override public Onboarding save(Onboarding doc) { return repo.save(doc); }

    @Override
    public String summary(Onboarding d) {
        return "%s %s · efektif %s".formatted("OFFBOARD".equals(d.getKind()) ? "Offboarding" : "Onboarding",
                hc.employeeName(d.getEmployeeId()), d.getEffectiveDate());
    }

    /** Posting = semua checklist selesai; offboarding menutup status karyawan (PRD GA aturan 3, HC #27). */
    @Override
    public void onPost(Onboarding d) {
        List<String> open = d.getTasks().stream().filter(t -> !t.isDone()).map(Onboarding.Task::getLabel).toList();
        if (!open.isEmpty()) {
            throw new BusinessException("OB_OPEN", "Checklist belum selesai: " + String.join("; ", open));
        }
        if ("OFFBOARD".equals(d.getKind())) {
            BigDecimal loan = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(principal - repaid_amount), 0) FROM hc.employee_loan
                    WHERE employee_id = ? AND status = 'POSTED'""", BigDecimal.class, d.getEmployeeId());
            BigDecimal adv = advances.outstanding(d.getEmployeeId());
            if (loan.signum() > 0 || adv.signum() > 0) {
                throw BusinessException.of("OB_DEBT", "Masih ada pinjaman Rp %,.0f dan uang muka Rp %,.0f yang belum diselesaikan", loan, adv);
            }
            jdbc.update("UPDATE hc.employee SET status = 'RESIGNED', end_date = ?, updated_at = now() WHERE id = ?",
                    Date.valueOf(d.getEffectiveDate()), d.getEmployeeId());
            if (!d.getEffectiveDate().isAfter(time.today())) {
                deactivateAccounts(d);
            }
        } else {
            jdbc.update("UPDATE hc.employee SET status = 'ACTIVE', join_date = LEAST(join_date, ?), updated_at = now() WHERE id = ?",
                    Date.valueOf(d.getEffectiveDate()), d.getEmployeeId());
            d.setApplied(true);
        }
    }

    void deactivateAccounts(Onboarding d) {
        List<Long> users = jdbc.queryForList("SELECT id FROM sys.app_user WHERE employee_id = ? AND active", Long.class, d.getEmployeeId());
        jdbc.update("UPDATE sys.app_user SET active = FALSE, updated_at = now() WHERE employee_id = ?", d.getEmployeeId());
        users.forEach(grants::evict);
        d.setApplied(true);
        log.info("Akun karyawan {} dinonaktifkan oleh offboarding {}", d.getEmployeeId(), d.getDocNo());
    }

    /** Setiap hari 00:30 WIB: nonaktifkan akun karyawan yang offboarding-nya efektif hari ini. */
    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Jakarta")
    @Transactional
    public void applyDueOffboarding() {
        for (Long id : jdbc.queryForList("""
                SELECT id FROM hc.onboarding WHERE kind = 'OFFBOARD' AND status = 'POSTED' AND NOT applied AND effective_date <= ?""",
                Long.class, Date.valueOf(time.today()))) {
            Onboarding d = load(id);
            deactivateAccounts(d);
            repo.save(d);
        }
    }
}

/** HC-05 API on/offboarding + centang checklist oleh departemen pemilik tugas. */
@RestController
@RequestMapping("/api/hc/onboardings")
class OnboardingController extends DocumentApi<Onboarding> {

    record TaskUpdate(boolean done, String note) {
    }

    private final OnboardingHandler handler;
    private final HcSupport hc;
    private final PermissionService perm;

    OnboardingController(OnboardingHandler handler, OnboardingRepository repo, Support support, HcSupport hc, PermissionService perm) {
        super(handler, repo, support, Onboarding.class);
        this.handler = handler;
        this.hc = hc;
        this.perm = perm;
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("applied");
    }

    @Override
    protected void beforeSave(Onboarding d, boolean isNew) {
        require(d.getEmployeeId() != null, "OB_EMP", "Karyawan wajib dipilih");
        require(List.of("ONBOARD", "OFFBOARD").contains(d.getKind()), "OB_KIND", "Jenis tidak dikenal");
        require(d.getEffectiveDate() != null, "OB_DATE", "Tanggal efektif wajib diisi");
        if (isNew || d.getTasks().isEmpty()) {
            d.getTasks().clear();
            short seq = 1;
            for (String[] t : "OFFBOARD".equals(d.getKind()) ? OnboardingHandler.OFFBOARD : OnboardingHandler.ONBOARD) {
                Onboarding.Task task = new Onboarding.Task();
                task.setOnboarding(d);
                task.setSeq(seq++);
                task.setCode(t[0]);
                task.setLabel(t[1]);
                task.setOwnerApp(t[2]);
                d.getTasks().add(task);
            }
        }
    }

    @Override
    protected void enrich(Map<String, Object> body, Onboarding d) {
        body.put("employeeName", hc.employeeName(d.getEmployeeId()));
        body.put("progress", d.getTasks().stream().filter(Onboarding.Task::isDone).count() + "/" + d.getTasks().size());
    }

    /** Pemilik tugas (aplikasi SYS/GA/FIN/HC) menandai tugasnya selesai. */
    @PutMapping("/{id}/tasks/{taskId}")
    @Transactional
    public Envelope updateTask(@PathVariable Long id, @PathVariable Long taskId, @RequestBody TaskUpdate req) {
        Onboarding d = handler.load(id);
        if (d.getStatus().name().equals("POSTED") || d.getStatus().name().equals("CANCELLED")) {
            throw new BusinessException("OB_STATE", "Checklist dokumen ini sudah ditutup");
        }
        Onboarding.Task t = d.getTasks().stream().filter(x -> x.getId().equals(taskId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Tugas", taskId));
        String app = "FIN".equals(t.getOwnerApp()) ? "FIN-30" : t.getOwnerApp() + "-01";
        if (!perm.has(app, Action.EDIT) && !perm.has("HC-05", Action.POST)) {
            throw new BusinessException("OB_OWNER", "Tugas ini milik aplikasi " + t.getOwnerApp());
        }
        t.setDone(req.done());
        t.setNote(req.note());
        t.setDoneBy(req.done() ? UserContext.userId() : null);
        t.setDoneAt(req.done() ? java.time.Instant.now() : null);
        handler.save(d);
        return envelope(d);
    }
}
