package id.herbatech.erp.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Mengubah JWT menjadi {@link CurrentUser}. Plant aktif dibaca dari header {@code X-Plant}
 * (pemilih plant di navbar) dan hanya dipakai bila pengguna memang punya akses ke plant itu.
 */
@Component
public class JwtUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String PLANT_HEADER = "X-Plant";

    private final UserGrantLoader loader;

    public JwtUserConverter(UserGrantLoader loader) {
        this.loader = loader;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Object uid = jwt.getClaim("uid");
        if (!(uid instanceof Number n)) {
            throw new DisabledException("Token tidak valid");
        }
        CurrentUser user = loader.load(n.longValue());
        if (user == null) {
            throw new DisabledException("Akun tidak aktif");
        }
        Long plant = requestedPlant();
        if (plant != null && canUsePlant(user, plant)) {
            user = user.withPlant(plant);
        }
        return new ErpAuthentication(user, jwt);
    }

    private static boolean canUsePlant(CurrentUser user, Long plant) {
        return user.grants().isEmpty() || user.grants().stream().anyMatch(g -> g.coversPlant(plant));
    }

    private static Long requestedPlant() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String h = req.getHeader(PLANT_HEADER);
            if (h != null && !h.isBlank()) {
                try {
                    return Long.parseLong(h.trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
