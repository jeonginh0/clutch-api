package jih.backend.auth.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AuthResponse {

    private static final String TOKEN_TYPE = "Bearer";

    private final String accessToken;
    private final String tokenType;
    private final long expiresIn;
    private final AuthUserResponse user;

    public static AuthResponse of(String accessToken, long expiresIn, AuthUserResponse user) {
        return new AuthResponse(accessToken, TOKEN_TYPE, expiresIn, user);
    }
}
