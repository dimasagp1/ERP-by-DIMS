package id.herbatech.erp.shared.security;

import id.herbatech.erp.shared.config.TimeService;
import id.herbatech.erp.shared.error.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Login, profil, preferensi pengguna (PRD §15.4) dan ganti kata sandi. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int MAX_FAILED = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final UserGrantLoader grants;
    private final PermissionService perm;
    private final TimeService time;
    private final JdbcTemplate jdbc;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, UserGrantLoader grants,
                          PermissionService perm, TimeService time, JdbcTemplate jdbc) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.grants = grants;
        this.perm = perm;
        this.time = time;
        this.jdbc = jdbc;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, MeResponse user) {
    }

    public record PlantRef(Long id, String code, String name) {
    }

    public record GrantRef(String role, String roleName, String app, Long plantId) {
    }

    public record Preferences(String locale, String theme, String density, String startPage, Long defaultPlantId,
                              String notifyPrefs) {
    }

    public record MeResponse(Long id, String username, String fullName, String email, Long employeeId,
                             Long plantId, List<PlantRef> plants, Set<String> apps, List<GrantRef> grants,
                             List<String> settingsApps, Preferences preferences) {
    }

    @PostMapping("/login")
    @Transactional(noRollbackFor = BusinessException.class)
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        AppUser user = users.findByUsernameIgnoreCase(req.username().trim()).orElse(null);
        Instant now = time.now();
        if (user == null || !user.isActive()) {
            throw invalid();
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new BusinessException("LOCKED", "Akun dikunci sementara karena terlalu banyak percobaan gagal. Coba lagi nanti.");
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            int failed = user.getFailedAttempts() + 1;
            user.setFailedAttempts(failed);
            if (failed >= MAX_FAILED) {
                user.setLockedUntil(now.plus(LOCK_DURATION));
                user.setFailedAttempts(0);
            }
            users.save(user);
            throw invalid();
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        users.save(user);
        grants.evict(user.getId());

        JwtService.IssuedToken token = jwt.issue(user);
        CurrentUser cu = grants.load(user.getId());
        return ResponseEntity.ok(new LoginResponse(token.token(), token.expiresAt(), me(cu, user)));
    }

    @GetMapping("/me")
    public MeResponse me() {
        CurrentUser cu = UserContext.current();
        return me(cu, users.findById(cu.id()).orElseThrow());
    }

    @PutMapping("/me/preferences")
    @Transactional
    public MeResponse updatePreferences(@RequestBody Preferences p) {
        CurrentUser cu = UserContext.current();
        AppUser user = users.findById(cu.id()).orElseThrow();
        if (p.locale() != null) user.setLocale(oneOf(p.locale(), "id", "en"));
        if (p.theme() != null) user.setTheme(oneOf(p.theme(), "light", "dark", "system"));
        if (p.density() != null) user.setDensity(oneOf(p.density(), "comfortable", "compact"));
        if (p.startPage() != null) user.setStartPage(oneOf(p.startPage(), "launcher", "last"));
        if (p.notifyPrefs() != null) user.setNotifyPrefs(p.notifyPrefs());
        if (p.defaultPlantId() != null) {
            if (plantsOf(cu).stream().noneMatch(pl -> pl.id().equals(p.defaultPlantId()))) {
                throw new BusinessException("PLANT", "Anda tidak memiliki akses ke plant tersebut");
            }
            user.setDefaultPlantId(p.defaultPlantId());
        }
        users.save(user);
        grants.evict(cu.id());
        return me(cu, user);
    }

    public record ChangePassword(@NotBlank String oldPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    @PostMapping("/me/password")
    @Transactional
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePassword req) {
        AppUser user = users.findById(UserContext.userId()).orElseThrow();
        if (!encoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException("PASSWORD", "Kata sandi lama salah");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.setPasswordChangedAt(time.now());
        users.save(user);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private MeResponse me(CurrentUser cu, AppUser user) {
        List<PlantRef> plants = plantsOf(cu);
        Long plant = cu.plantId() != null ? cu.plantId() : plants.isEmpty() ? null : plants.getFirst().id();
        List<GrantRef> grantRefs = cu.grants().stream()
                .map(g -> new GrantRef(g.roleCode(), g.roleName(), g.appCode(), g.plantId())).toList();
        Set<String> apps = perm.visibleApps(cu);
        List<String> settingsApps = apps.stream().filter(app -> cu.grants().stream().anyMatch(g -> g.coversApp(app)
                && (g.roleCode().equals("MANAGER") || g.roleCode().equals("ADMIN")))).toList();
        return new MeResponse(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), user.getEmployeeId(),
                plant, plants, apps, grantRefs, settingsApps,
                new Preferences(user.getLocale(), user.getTheme(), user.getDensity(), user.getStartPage(),
                        user.getDefaultPlantId(), user.getNotifyPrefs()));
    }

    private List<PlantRef> plantsOf(CurrentUser cu) {
        List<PlantRef> all = jdbc.query("SELECT id, code, name FROM sys.plant WHERE active ORDER BY code",
                (rs, i) -> new PlantRef(rs.getLong("id"), rs.getString("code"), rs.getString("name")));
        boolean unrestricted = cu.grants().isEmpty() || cu.grants().stream().anyMatch(g -> g.plantId() == null);
        if (unrestricted) {
            return all;
        }
        Set<Long> allowed = cu.grants().stream().map(CurrentUser.Grant::plantId).collect(java.util.stream.Collectors.toSet());
        return all.stream().filter(p -> allowed.contains(p.id())).toList();
    }

    private static String oneOf(String v, String... allowed) {
        for (String a : allowed) {
            if (a.equals(v)) {
                return v;
            }
        }
        throw new BusinessException("PREF", "Nilai preferensi tidak dikenal: " + v);
    }

    private static BusinessException invalid() {
        return new BusinessException("LOGIN", "Nama pengguna atau kata sandi salah");
    }
}
