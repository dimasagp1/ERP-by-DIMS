package id.herbatech.erp.hc;

import id.herbatech.erp.shared.document.DocumentApi;
import id.herbatech.erp.shared.document.DocumentHandler;
import id.herbatech.erp.shared.document.DocumentRepository;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.security.CurrentUser;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

interface OvertimeRequestRepository extends DocumentRepository<OvertimeRequest> {
}

@Component
class OvertimeHandler implements DocumentHandler<OvertimeRequest> {

    /** Batas lembur pada hari kerja (PP 35/2021: maks. 4 jam sehari). */
    static final BigDecimal MAX_WORKDAY_HOURS = BigDecimal.valueOf(4);

    private final OvertimeRequestRepository repo;
    private final AttendanceService attendance;
    private final HcSupport hc;

    OvertimeHandler(OvertimeRequestRepository repo, AttendanceService attendance, HcSupport hc) {
        this.repo = repo;
        this.attendance = attendance;
        this.hc = hc;
    }

    @Override public String docType() { return "OT"; }
    @Override public String periodModule() { return "HC"; }
    @Override public OvertimeRequest load(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Lembur", id)); }
    @Override public OvertimeRequest save(OvertimeRequest doc) { return repo.save(doc); }

    @Override
    public String summary(OvertimeRequest d) {
        int n = d.getLines().size();
        String who = n == 1 ? hc.employeeName(d.getLines().getFirst().getEmployeeId()) : n + " orang";
        return "Lembur %s · %s · %s jam · %s".formatted(d.getWorkDate(), who,
                d.getTotalHours().stripTrailingZeros().toPlainString(), d.getReason());
    }

    @Override
    public void validateSubmit(OvertimeRequest d) {
        if (d.getLines().isEmpty()) {
            throw new BusinessException("OT_LINES", "Isi minimal satu karyawan");
        }
        if (d.getReason() == null || d.getReason().isBlank()) {
            throw new BusinessException("OT_REASON", "Alasan lembur wajib diisi");
        }
        attendance.assertOpen(d.getWorkDate());
        Set<Long> seen = new HashSet<>();
        for (OvertimeRequest.Line l : d.getLines()) {
            if (!seen.add(l.getEmployeeId())) {
                throw new BusinessException("OT_DUP", "Karyawan " + hc.employeeName(l.getEmployeeId()) + " tercantum lebih dari sekali");
            }
            hc.requireActiveEmployee(l.getEmployeeId());
            if ("WORKDAY".equals(d.getDayType()) && l.getHours().compareTo(MAX_WORKDAY_HOURS) > 0) {
                throw new BusinessException("OT_MAX", "Lembur hari kerja maksimal 4 jam sehari (PP 35/2021): "
                        + hc.employeeName(l.getEmployeeId()));
            }
        }
    }
}

/** HC-08 & ESS-02: API surat perintah lembur. Lembur tanpa approval tidak dibayar (PRD HC aturan 3). */
@RestController
@RequestMapping("/api/hc/overtimes")
class OvertimeController extends DocumentApi<OvertimeRequest> {

    private final HcSupport hc;

    OvertimeController(OvertimeHandler handler, OvertimeRequestRepository repo, Support support, HcSupport hc) {
        super(handler, repo, support, OvertimeRequest.class);
        this.hc = hc;
    }

    @Override
    protected List<String> searchFields() {
        return List.of("docNo", "reason", "reference");
    }

    @Override
    protected List<String> protectedFields() {
        return List.of("totalHours");
    }

    @Override
    protected void apply(OvertimeRequest target, OvertimeRequest in, boolean isNew) {
        super.apply(target, in, isNew);
        target.getLines().clear();
        short no = 1;
        for (OvertimeRequest.Line l : in.getLines()) {
            l.setId(null);
            l.setRequest(target);
            l.setLineNo(no++);
            target.getLines().add(l);
        }
    }

    @Override
    protected void beforeSave(OvertimeRequest d, boolean isNew) {
        require(d.getWorkDate() != null, "OT_DATE", "Tanggal lembur wajib diisi");
        require(List.of("WORKDAY", "RESTDAY", "HOLIDAY").contains(d.getDayType()), "OT_DAYTYPE", "Jenis hari tidak dikenal");
        if (!hc.actsForOthers("HC-08")) {
            Long me = hc.myEmployeeId();
            if (d.getLines().isEmpty()) {
                OvertimeRequest.Line l = new OvertimeRequest.Line();
                l.setRequest(d);
                l.setLineNo((short) 1);
                d.getLines().add(l);
            }
            require(d.getLines().size() == 1, "OT_SELF", "Pengajuan mandiri hanya untuk diri sendiri");
            d.getLines().getFirst().setEmployeeId(me);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (OvertimeRequest.Line l : d.getLines()) {
            require(l.getEmployeeId() != null, "OT_EMP", "Karyawan wajib dipilih di setiap baris");
            require(l.getStartTime() != null && l.getEndTime() != null, "OT_TIME", "Jam mulai & selesai wajib diisi");
            long minutes = Duration.between(l.getStartTime(), l.getEndTime()).toMinutes();
            if (minutes <= 0) {
                minutes += 24 * 60;
            }
            l.setHours(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
            total = total.add(l.getHours());
        }
        d.setTotalHours(total);
    }

    @Override
    protected Predicate mine(Root<OvertimeRequest> root, CriteriaQuery<?> q, CriteriaBuilder cb, CurrentUser me) {
        if (me.employeeId() == null) {
            return cb.equal(root.get("createdBy"), me.id());
        }
        Subquery<Long> sq = q.subquery(Long.class);
        Root<OvertimeRequest.Line> l = sq.from(OvertimeRequest.Line.class);
        sq.select(l.get("request").get("id")).where(cb.equal(l.get("employeeId"), me.employeeId()));
        return cb.or(cb.equal(root.get("createdBy"), me.id()), root.get("id").in(sq));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void enrich(Map<String, Object> body, OvertimeRequest d) {
        Map<Long, String> names = hc.employeeNames(d.getLines().stream().map(OvertimeRequest.Line::getEmployeeId).toList());
        Object lines = body.get("lines");
        if (lines instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                Object id = m.get("employeeId");
                m.put("employeeName", id == null ? null : names.get(((Number) id).longValue()));
            }
        }
    }
}
