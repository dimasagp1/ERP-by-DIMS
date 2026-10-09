package id.herbatech.erp.sys;

import id.herbatech.erp.shared.approval.ApprovalRule;
import id.herbatech.erp.shared.approval.ApprovalRuleRepository;
import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.document.DocType;
import id.herbatech.erp.shared.document.DocTypeRepository;
import id.herbatech.erp.shared.error.BusinessException;
import id.herbatech.erp.shared.security.Action;
import id.herbatech.erp.shared.security.AppUser;
import id.herbatech.erp.shared.security.AppUserRepository;
import id.herbatech.erp.shared.security.PermissionService;
import id.herbatech.erp.shared.security.Role;
import id.herbatech.erp.shared.security.RoleMenuPermission;
import id.herbatech.erp.shared.security.RoleMenuPermissionRepository;
import id.herbatech.erp.shared.security.RoleRepository;
import id.herbatech.erp.shared.security.UserGrantLoader;
import id.herbatech.erp.shared.security.UserRole;
import id.herbatech.erp.shared.security.UserRoleRepository;
import id.herbatech.erp.shared.web.MasterController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** SYS-03 Pengguna & Peran, SYS-04 Matriks Approval, SYS-05 Penomoran Dokumen. */
final class AccessAdminControllers {

    private AccessAdminControllers() {
    }

    // ---------------------------------------------------------------- SYS-03 Pengguna

    @RestController
    @RequestMapping("/api/sys/users")
    static class UserController extends MasterController<AppUser> {

        private final UserRoleRepository userRoles;
        private final PasswordEncoder encoder;
        private final UserGrantLoader grants;
        private final TimeService time;

        UserController(AppUserRepository r, PermissionService p, UserRoleRepository userRoles, PasswordEncoder encoder,
                       UserGrantLoader grants, TimeService time) {
            super(r, p, AppUser.class);
            this.userRoles = userRoles;
            this.encoder = encoder;
            this.grants = grants;
            this.time = time;
        }

        @Override protected String menuCode() { return "SYS-03"; }
        @Override protected List<String> searchFields() { return List.of("username", "fullName", "email"); }
        @Override protected Sort defaultSort() { return Sort.by("username"); }
        @Override protected String[] immutableFields() { return new String[]{"username"}; }

        @Override
        protected void beforeSave(AppUser u, AppUser existing) {
            u.setUsername(u.getUsername().trim().toLowerCase());
            if (!u.getUsername().matches("[a-z0-9._-]{3,64}")) {
                throw new BusinessException("USERNAME", "Nama pengguna 3–64 karakter: huruf kecil, angka, titik, garis bawah, tanda hubung");
            }
            if (existing == null) {
                // Akun baru belum bisa login sampai admin menetapkan kata sandi awal.
                u.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
            } else {
                // Field yang tidak dikirim klien (read-only) dipertahankan dari data lama.
                u.setPasswordHash(existing.getPasswordHash());
                u.setFailedAttempts(existing.getFailedAttempts());
                u.setLockedUntil(existing.getLockedUntil());
                u.setLastLoginAt(existing.getLastLoginAt());
                u.setPasswordChangedAt(existing.getPasswordChangedAt());
                grants.evict(existing.getId());
            }
        }

        public record PasswordReset(@NotBlank @Size(min = 8, max = 72) String newPassword) {
        }

        @PostMapping("/{id}/password")
        @Transactional
        public ResponseEntity<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordReset req) {
            requireWrite(Action.EDIT);
            AppUser u = find(id);
            u.setPasswordHash(encoder.encode(req.newPassword()));
            u.setPasswordChangedAt(time.now());
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
            repo.save(u);
            return ResponseEntity.noContent().build();
        }

        public record RoleAssignment(@NotNull Long roleId, @NotBlank String appCode, Long plantId) {
        }

        @GetMapping("/{id}/roles")
        public List<UserRole> roles(@PathVariable Long id) {
            perm.require(menuCode(), Action.VIEW);
            return userRoles.findByUserId(id);
        }

        @PutMapping("/{id}/roles")
        @Transactional
        public List<UserRole> setRoles(@PathVariable Long id, @Valid @RequestBody List<RoleAssignment> roles) {
            requireWrite(Action.EDIT);
            find(id);
            userRoles.deleteByUserId(id);
            userRoles.flush();
            List<UserRole> saved = userRoles.saveAll(roles.stream().distinct()
                    .map(r -> new UserRole(id, r.roleId(), r.appCode().toUpperCase().equals("*") ? "*" : r.appCode().toUpperCase(), r.plantId()))
                    .toList());
            grants.evict(id);
            return saved;
        }
    }

    // ---------------------------------------------------------------- SYS-03 Peran

    @RestController
    @RequestMapping("/api/sys/roles")
    static class RoleController extends MasterController<Role> {

        private final RoleMenuPermissionRepository overrides;
        private final UserGrantLoader grants;

        RoleController(RoleRepository r, PermissionService p, RoleMenuPermissionRepository overrides, UserGrantLoader grants) {
            super(r, p, Role.class);
            this.overrides = overrides;
            this.grants = grants;
        }

        @Override protected String menuCode() { return "SYS-03"; }
        @Override protected String[] immutableFields() { return new String[]{"code"}; }

        @Override
        protected void beforeSave(Role role, Role existing) {
            role.setCode(role.getCode().trim().toUpperCase());
            try {
                role.actionSet();
            } catch (IllegalArgumentException e) {
                throw new BusinessException("ACTIONS", "Aksi tidak dikenal. Pilihan: " + Arrays.toString(Action.values()));
            }
            if (existing != null) {
                role.setBuiltin(existing.isBuiltin());
            }
            grants.evictAll();
        }

        @GetMapping("/{id}/overrides")
        public List<RoleMenuPermission> getOverrides(@PathVariable Long id) {
            perm.require(menuCode(), Action.VIEW);
            return overrides.findByRoleId(id);
        }

        @PutMapping("/{id}/overrides")
        @Transactional
        public List<RoleMenuPermission> setOverrides(@PathVariable Long id, @RequestBody List<RoleMenuPermission> list) {
            requireWrite(Action.EDIT);
            find(id);
            overrides.deleteAll(overrides.findByRoleId(id));
            overrides.flush();
            list.forEach(o -> {
                o.setId(null);
                o.setRoleId(id);
            });
            List<RoleMenuPermission> saved = overrides.saveAll(list);
            grants.evictAll();
            return saved;
        }
    }

    // ---------------------------------------------------------------- SYS-04 Matriks approval

    @RestController
    @RequestMapping("/api/sys/approval-rules")
    static class ApprovalRuleController extends MasterController<ApprovalRule> {
        ApprovalRuleController(ApprovalRuleRepository r, PermissionService p) {
            super(r, p, ApprovalRule.class);
        }

        @Override protected String menuCode() { return "SYS-04"; }
        /** Admin Sistem + FIN (PRD §3). */
        @Override protected List<String> ownerMenus() { return List.of("FIN-51"); }
        @Override protected List<String> searchFields() { return List.of("docTypeCode", "label"); }
        @Override protected Sort defaultSort() { return Sort.by("docTypeCode", "level"); }

        @Override
        protected void beforeSave(ApprovalRule r, ApprovalRule existing) {
            switch (r.getApproverType()) {
                case "ROLE" -> {
                    if (r.getApproverRole() == null || r.getApproverRole().isBlank()) {
                        throw new BusinessException("RULE", "Peran approver wajib diisi untuk tipe ROLE");
                    }
                    if (r.getApproverApp() == null || r.getApproverApp().isBlank()) {
                        r.setApproverApp("*");
                    }
                    r.setApproverUserId(null);
                }
                case "USER" -> {
                    if (r.getApproverUserId() == null) {
                        throw new BusinessException("RULE", "Pengguna approver wajib dipilih untuk tipe USER");
                    }
                    r.setApproverRole(null);
                    r.setApproverApp(null);
                }
                default -> {
                    r.setApproverRole(null);
                    r.setApproverApp(null);
                    r.setApproverUserId(null);
                }
            }
        }
    }

    // ---------------------------------------------------------------- SYS-05 Penomoran / jenis dokumen

    @RestController
    @RequestMapping("/api/sys/doc-types")
    static class DocTypeController extends MasterController<DocType> {
        DocTypeController(DocTypeRepository r, PermissionService p) {
            super(r, p, DocType.class);
        }

        @Override protected String menuCode() { return "SYS-05"; }
        @Override protected String[] immutableFields() { return new String[]{"code", "appCode"}; }
    }
}
