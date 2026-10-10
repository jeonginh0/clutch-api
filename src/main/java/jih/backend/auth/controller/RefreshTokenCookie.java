package jih.backend.auth.controller;

import java.time.Duration;
import jih.backend.global.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookie {

    public static final String NAME = "clutch_refresh";
    private static final String PATH = "/api/v1/auth";

    private final JwtProperties jwtProperties;

    public ResponseCookie create(String token, Duration ttl) {
        return base(token).maxAge(ttl).build();
    }

    public ResponseCookie expire() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
            .httpOnly(true)
            .secure(jwtProperties.isRefreshCookieSecure())
            .sameSite("Strict")
            .path(PATH);
    }
}
