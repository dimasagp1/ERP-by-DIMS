package id.herbatech.erp.shared.web;

import id.herbatech.erp.shared.audit.AuditContext;
import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.error.NotFoundException;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.PermissionService;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controller master data generik (pola "Master data" PRD §15.3: list + form ringkas + riwayat perubahan).
 * Subkelas cukup menentukan menu, kolom pencarian, dan aturan validasi khusus.
 * Master tidak dihapus: tombol hapus menonaktifkan data agar referensi historis tetap utuh.
 */
public abstract class MasterController<E extends BaseEntity> {

    private static final String[] SYSTEM_FIELDS = {"id", "version", "createdAt", "createdBy", "updatedAt", "updatedBy"};

    protected final MasterRepository<E> repo;
    protected final PermissionService perm;
    private final Class<E> type;

    protected MasterController(MasterRepository<E> repo, PermissionService perm, Class<E> type) {
        this.repo = repo;
        this.perm = perm;
        this.type = type;
    }

    /** Kode menu untuk hak akses, mis. {@code SYS-06}. */
    protected abstract String menuCode();

    /**
     * Menu departemen pemilik data (PRD §3) yang juga boleh membuat/mengubah master ini,
     * mis. Item Master dimiliki Inventory Control (SCM-43).
     */
    protected List<String> ownerMenus() {
        return List.of();
    }

    /** Kolom yang dicari oleh parameter {@code q}. */
    protected List<String> searchFields() {
        return List.of("code", "name");
    }

    protected Sort defaultSort() {
        return Sort.by("code");
    }

    /** Validasi/normalisasi khusus sebelum simpan. {@code existing} null saat membuat baru. */
    protected void beforeSave(E entity, E existing) {
    }

    /** Field yang tidak boleh diubah setelah dibuat (mis. kode). */
    protected String[] immutableFields() {
        return new String[0];
    }

    @GetMapping
    public PageResponse<E> list(@RequestParam Map<String, String> params) {
        perm.require(menuCode(), Action.VIEW);
        return PageResponse.of(repo.findAll(Specs.fromParams(params, searchFields()), Specs.pageable(params, defaultSort(), type)));
    }

    @GetMapping("/{id}")
    public E get(@PathVariable Long id) {
        perm.require(menuCode(), Action.VIEW);
        return find(id);
    }

    @PostMapping
    @Transactional
    public E create(@Valid @RequestBody E body) {
        requireWrite(Action.CREATE);
        body.setId(null);
        body.setVersion(null);
        beforeSave(body, null);
        return repo.save(body);
    }

    @PutMapping("/{id}")
    @Transactional
    public E update(@PathVariable Long id, @Valid @RequestBody E body, @RequestParam(required = false) String reason) {
        requireWrite(Action.EDIT);
        E existing = find(id);
        if (body.getVersion() != null && !Objects.equals(body.getVersion(), existing.getVersion())) {
            throw new OptimisticLockingFailureException("version mismatch");
        }
        for (String f : immutableFields()) {
            Object before = new org.springframework.beans.BeanWrapperImpl(existing).getPropertyValue(f);
            Object after = new org.springframework.beans.BeanWrapperImpl(body).getPropertyValue(f);
            if (!Objects.equals(before, after)) {
                throw new BusinessException("IMMUTABLE", "Kolom " + f + " tidak boleh diubah setelah data dibuat");
            }
        }
        beforeSave(body, existing);
        BeanUtils.copyProperties(body, existing, SYSTEM_FIELDS);
        return AuditContext.withReason(reason, () -> repo.saveAndFlush(existing));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deactivate(@PathVariable Long id, @RequestParam(required = false) String reason) {
        requireWrite(Action.EDIT);
        E existing = find(id);
        if (existing instanceof Activatable a) {
            AuditContext.withReason(reason, () -> {
                a.setActive(false);
                return repo.saveAndFlush(existing);
            });
        } else {
            repo.delete(existing);
            repo.flush();
        }
        return ResponseEntity.noContent().build();
    }

    protected void requireWrite(Action action) {
        if (perm.has(menuCode(), action) || ownerMenus().stream().anyMatch(m -> perm.has(m, action))) {
            return;
        }
        perm.require(menuCode(), action);
    }

    protected E find(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException(type.getSimpleName(), id));
    }
}
