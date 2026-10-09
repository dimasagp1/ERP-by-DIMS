package id.herbatech.erp.shared.security;

import id.herbatech.erp.shared.config.ErpProperties;
import id.herbatech.erp.shared.config.TimeService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/** Menerbitkan dan memverifikasi token akses (JWT HS256). */
@Component
public class JwtService {

    public static final String ISSUER = "herbatech-erp";

    private final SecretKey key;
    private final JwtEncoder encoder;
    private final Duration ttl;
    private final TimeService time;

    public JwtService(ErpProperties props, TimeService time) {
        String secret = props.security().jwtSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("erp.security.jwt-secret wajib diisi minimal 32 karakter");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).build();
        this.ttl = Duration.ofHours(props.security().tokenTtlHours());
        this.time = time;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public IssuedToken issue(AppUser user) {
        Instant now = time.now();
        Instant exp = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(exp)
                .claim("uid", user.getId())
                .claim("name", user.getFullName())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), exp);
    }

    public JwtDecoder decoder() {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
