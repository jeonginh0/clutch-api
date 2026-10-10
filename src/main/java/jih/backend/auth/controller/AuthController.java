package jih.backend.auth.controller;

import jakarta.validation.Valid;
import jih.backend.auth.dto.AuthResponse;
import jih.backend.auth.dto.LoginRequest;
import jih.backend.auth.dto.SignupRequest;
import jih.backend.auth.service.AuthResult;
import jih.backend.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return withTokens(HttpStatus.CREATED, authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withTokens(HttpStatus.OK, authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
        @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        return withTokens(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @AuthenticationPrincipal Jwt accessToken,
        @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        authService.logout(accessToken.getId(), accessToken.getExpiresAt(), refreshToken);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.expire().toString())
            .build();
    }

    private ResponseEntity<AuthResponse> withTokens(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
            .header(HttpHeaders.SET_COOKIE,
                refreshTokenCookie.create(result.getRefreshToken(), result.getRefreshTokenTtl()).toString())
            .body(AuthResponse.of(result.getAccessToken(), result.getAccessTokenExpiresIn(), result.getUser()));
    }
}
