package id.herbatech.erp.shared.document;

import id.herbatech.erp.shared.domain.BaseEntity;
import id.herbatech.erp.shared.domain.DocumentEntity;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.CurrentUser;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.UserContext;
import id.herbatech.erp.shared.security.UserDirectory;
import id.herbatech.erp.shared.web.PageResponse;
import id.herbatech.erp.shared.web.Specs;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import tools.jackson.databind.ObjectMapper;

import java.beans.PropertyDescriptor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API generik dokumen transaksi (PRD §15.2 level 2 & 3): daftar dengan filter & cakupan data, detail dengan
 * metadata status & tombol aksi, buat dan ubah draft. Subkelas cukup menulis aturan khas dokumennya.
 * <p>
 * Body request memakai entitas itu sendiri; kolom standar dokumen bersifat read-only sehingga klien tidak bisa
 * mengisi status, nomor, atau jejak approval.
 */
public abstract class DocumentApi<D extends DocumentEntity> {

    /** Dependensi bersama agar konstruktor subkelas tetap ringkas. */
    @Component
    public static class Support {
        final DocumentWorkflowService workflow;
        final DocumentAccess access;
        final PermissionService perm;
        final UserDirectory users;
        final ObjectMapper mapper;
        final id.herbatech.erp.shared.config.TimeService time;

        public Support(DocumentWorkflowService workflow, DocumentAccess access, PermissionService perm,
                       UserDirectory users, ObjectMapper mapper, id.herbatech.erp.shared.config.TimeService time) {
            this.workflow = workflow;
            this.access = access;
            this.perm = perm;
            this.users = users;
            this.mapper = mapper;
            this.time = time;
        }
    }

    /** Metadata dokumen untuk header form: pipeline status, tombol aksi, jejak waktu. */
    public record Meta(String docType, String docNo, String status, List<String> actions, String createdByName,
                       Instant createdAt, Instant submittedAt, String approvedByName, Instant approvedAt,
                       String postedByName, Instant postedAt, String cancelReason, Integer version, boolean canEditAll) {
    }

    public record Envelope(Map<String, Object> doc, Meta meta) {
    }

    private static final Set<String> BASE_FIELDS = Set.of("id", "version", "createdAt", "createdBy", "updatedAt", "updatedBy",
            "docNo", "docDate", "plantId", "status", "submittedAt", "approvedBy", "approvedAt", "postedBy", "postedAt",
            "cancelledBy", "cancelledAt", "cancelReason");

    protected final DocumentHandler<D> handler;
    protected final DocumentRepository<D> repo;
    protected final Support s;
    private final Class<D> type;

    protected DocumentApi(DocumentHandler<D> handler, DocumentRepository<D> repo, Support support, Class<D> type) {
        this.handler = handler;
        this.repo = repo;
        this.s = support;
        this.type = type;
    }

    // ------------------------------------------------------------------ hook subkelas

    protected List<String> searchFields() {
        return List.of("docNo");
    }

    /** Salin field bisnis dari request ke dokumen (bawaan: semua properti sederhana selain kolom standar & koleksi). */
    protected void apply(D target, D incoming, boolean isNew) {
        List<String> ignore = new ArrayList<>(BASE_FIELDS);
        for (PropertyDescriptor pd : BeanUtils.getPropertyDescriptors(type)) {
            if (Collection.class.isAssignableFrom(pd.getPropertyType())) {
                ignore.add(pd.getName());
            }
        }
        ignore.addAll(protectedFields());
        BeanUtils.copyProperties(incoming, target, ignore.toArray(String[]::new));
    }

    /** Field tambahan yang tidak boleh diisi klien (mis. hasil hitung). */
    protected List<String> protectedFields() {
        return List.of();
    }

    /** Validasi & normalisasi sebelum simpan (setelah apply). */
    protected void beforeSave(D doc, boolean isNew) {
    }

    /** Tambahan isi respons (label lookup, ringkasan) — dijalankan di dalam transaksi. */
    protected void enrich(Map<String, Object> body, D doc) {
    }

    /** Filter "dokumen saya" untuk menu Layanan Saya (bawaan: pembuat = saya). */
    protected Predicate mine(Root<D> root, CriteriaQuery<?> query, CriteriaBuilder cb, CurrentUser me) {
        return cb.equal(root.get("createdBy"), me.id());
    }

    protected DocumentWorkflowService workflow() {
        return s.workflow;
    }

    protected DocType docType() {
        return s.workflow.type(handler.docType());
    }

    // ------------------------------------------------------------------ endpoint

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(@RequestParam Map<String, String> params) {
        DocType dt = docType();
        CurrentUser me = UserContext.current();
        boolean mine = "true".equals(params.get("mine"));
        if (!mine) {
            s.perm.require(dt.getMenuCode(), Action.VIEW);
        }
        Set<Long> visible = mine ? null : s.access.visibleCreators(dt);
        Specification<D> spec = Specs.<D>fromParams(params, searchFields()).and((root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("plantId"), me.plantId()));
            if (mine) {
                ps.add(mine(root, q, cb, me));
            } else if (visible != null) {
                ps.add(root.get("createdBy").in(visible));
            }
            if (params.get("from") != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("docDate"), LocalDate.parse(params.get("from"))));
            }
            if (params.get("to") != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("docDate"), LocalDate.parse(params.get("to"))));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        });
        Page<D> page = repo.findAll(spec, Specs.pageable(params, Sort.by("docDate").descending(), type));
        Map<Long, String> names = s.users.names(page.getContent().stream().map(BaseEntity::getCreatedBy).toList());
        return PageResponse.of(page, d -> {
            Map<String, Object> row = toMap(d, false);
            row.put("createdByName", d.getCreatedBy() == null ? "Sistem" : names.get(d.getCreatedBy()));
            enrich(row, d);
            return row;
        });
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Envelope get(@PathVariable Long id) {
        D doc = handler.load(id);
        s.access.requireView(docType(), doc, handler.requesterId(doc));
        return envelope(doc);
    }

    @PostMapping
    @Transactional
    public Envelope create(@RequestBody D body) {
        D doc = BeanUtils.instantiateClass(type);
        doc.setDocDate(body.getDocDate() != null ? body.getDocDate() : s.time.today());
        apply(doc, body, true);
        beforeSave(doc, true);
        D saved = s.workflow.initDraft(handler, doc);
        return envelope(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public Envelope update(@PathVariable Long id, @RequestBody D body) {
        D doc = handler.load(id);
        if (body.getVersion() != null && !body.getVersion().equals(doc.getVersion())) {
            throw new OptimisticLockingFailureException("version");
        }
        s.workflow.assertEditable(handler, doc);
        if (body.getDocDate() != null) {
            doc.setDocDate(body.getDocDate());
        }
        apply(doc, body, false);
        beforeSave(doc, false);
        s.workflow.assertEditable(handler, doc);
        D saved = handler.save(doc);
        s.workflow.touched(handler, saved);
        return envelope(saved);
    }

    // ------------------------------------------------------------------ util

    protected Envelope envelope(D doc) {
        Map<String, Object> body = toMap(doc, true);
        enrich(body, doc);
        Map<Long, String> names = s.users.names(java.util.Arrays.asList(doc.getCreatedBy(), doc.getApprovedBy(), doc.getPostedBy()));
        Meta meta = new Meta(handler.docType(), doc.getDocNo(), doc.getStatus().name(),
                s.workflow.allowedActions(handler.docType(), doc),
                doc.getCreatedBy() == null ? "Sistem" : names.get(doc.getCreatedBy()), doc.getCreatedAt(), doc.getSubmittedAt(),
                names.get(doc.getApprovedBy()), doc.getApprovedAt(), names.get(doc.getPostedBy()), doc.getPostedAt(),
                doc.getCancelReason(), doc.getVersion(), s.perm.has(docType().getMenuCode(), Action.CREATE));
        return new Envelope(body, meta);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> toMap(D doc, boolean withCollections) {
        Map<String, Object> m = s.mapper.convertValue(doc, LinkedHashMap.class);
        if (!withCollections) {
            m.entrySet().removeIf(e -> e.getValue() instanceof Collection<?>);
        }
        return m;
    }

    protected static void require(boolean condition, String code, String message) {
        if (!condition) {
            throw new BusinessException(code, message);
        }
    }
}
