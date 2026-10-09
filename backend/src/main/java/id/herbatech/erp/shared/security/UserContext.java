package id.herbatech.erp.shared.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Akses statis ke pengguna yang sedang login. */
public final class UserContext {

    private UserContext() {
    }

    public static Optional<CurrentUser> currentOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static CurrentUser current() {
        return currentOptional().orElseThrow(() -> new AccessDeniedException("Sesi tidak valid, silakan login ulang"));
    }

    public static Long userId() {
        return current().id();
    }

    public static Long plantId() {
        return current().plantId();
    }
}
