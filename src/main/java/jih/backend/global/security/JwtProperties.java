package jih.backend.global.security;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
@ConfigurationProperties(prefix = "clutch.jwt")
public class JwtProperties {

    private static final int MIN_SECRET_BYTES = 32;

    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final boolean refreshCookieSecure;
    private final SecretKey signingKey;

    public JwtProperties(String secret,
        String issuer,
        Duration accessTtl,
        Duration refreshTtl,
        @DefaultValue("false") boolean refreshCookieSecure) {
        this.issuer = issuer;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.refreshCookieSecure = refreshCookieSecure;
        this.signingKey = toSigningKey(secret);
    }

    private static SecretKey toSigningKey(String secret) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException(
                "clutch.jwt.secret(JWT_SECRET)는 Base64 문자열이어야 합니다. `openssl rand -base64 32`로 생성하세요.", e);
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("clutch.jwt.secret(JWT_SECRET)는 32바이트 이상이어야 합니다.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

}
