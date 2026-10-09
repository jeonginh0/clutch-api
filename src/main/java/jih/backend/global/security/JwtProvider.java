package jih.backend.global.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import jih.backend.user.domain.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public JwtProvider(JwtProperties jwtProperties, Clock clock) {
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(jwtProperties.getSigningKey()));
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public String issueAccessToken(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.getIssuer())
            .subject(user.getPublicId().toString())
            .id(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plus(jwtProperties.getAccessTtl()))
            .claim(ROLE_CLAIM, user.getRole().name())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getAccessTokenExpiresInSeconds() {
        return jwtProperties.getAccessTtl().toSeconds();
    }
}
