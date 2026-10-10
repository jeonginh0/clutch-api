package jih.backend.auth.service;

import java.time.Duration;
import jih.backend.auth.dto.AuthUserResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AuthResult {

    private final String accessToken;
    private final long accessTokenExpiresIn;
    private final String refreshToken;
    private final Duration refreshTokenTtl;
    private final AuthUserResponse user;
}
