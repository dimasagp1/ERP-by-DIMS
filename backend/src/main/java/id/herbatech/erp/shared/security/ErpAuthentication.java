package id.herbatech.erp.shared.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.stream.Collectors;

/** Authentication yang principal-nya {@link CurrentUser} lengkap dengan penugasan peran. */
public class ErpAuthentication extends AbstractAuthenticationToken {

    private final CurrentUser user;
    private final Jwt jwt;

    public ErpAuthentication(CurrentUser user, Jwt jwt) {
        super(user.grants().stream()
                .map(g -> new SimpleGrantedAuthority("ROLE_" + g.roleCode()))
                .collect(Collectors.toSet()));
        this.user = user;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }

    @Override
    public CurrentUser getPrincipal() {
        return user;
    }

    @Override
    public String getName() {
        return user.username();
    }
}
