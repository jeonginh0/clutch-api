package jih.backend.auth.service;

import java.time.Instant;
import jih.backend.auth.dto.LoginRequest;
import jih.backend.auth.dto.SignupRequest;

public interface AuthService {

    AuthResult signup(SignupRequest request);

    AuthResult login(LoginRequest request);

    AuthResult refresh(String refreshToken);

    void logout(String accessTokenId, Instant accessTokenExpiresAt, String refreshToken);
}
