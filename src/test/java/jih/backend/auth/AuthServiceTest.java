package jih.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import jih.backend.auth.dto.LoginRequest;
import jih.backend.auth.dto.SignupRequest;
import jih.backend.auth.service.AuthResult;
import jih.backend.auth.service.AuthService;
import jih.backend.global.error.BusinessException;
import jih.backend.global.error.ErrorCode;
import jih.backend.support.IntegrationTestSupport;
import jih.backend.user.domain.User;
import jih.backend.user.repository.UserRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

public class AuthServiceTest extends IntegrationTestSupport {
    private static final String EMAIL = "player1@clutch.gg";
    private static final String PASSWORD = "clutch1234";
    private static final String NICKNAME = "player_one";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    //회원가입 테스트
    @Test
    @DisplayName("[1.1.1] 가입하면 이메일은 소문자로, 비밀번호는 해시로 저장된다")
    void signup_savesNormalizedEmailAndHashedPassword() {
        AuthResult result = authService.signup(new SignupRequest("Player1@Clutch.GG", PASSWORD, NICKNAME));

        User saved = userRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(result.getUser().getPublicId()).isEqualTo(saved.getPublicId());
        assertThat(result.getAccessToken()).isNotBlank();
        assertThat(result.getRefreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("[1.1.1] 액세스 토큰의 subject는 내부 id가 아니라 public_id다")
    void signup_accessTokenSubjectIsPublicId() {
        AuthResult result = authService.signup(new SignupRequest(EMAIL, PASSWORD, NICKNAME));

        Jwt jwt = jwtDecoder.decode(result.getAccessToken());
        assertThat(jwt.getSubject()).isEqualTo(result.getUser().getPublicId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    @DisplayName("[1.1.1] 대소문자만 다른 이메일로는 가입할 수 없다")
    void signup_duplicateEmailIgnoringCase() {
        authService.signup(new SignupRequest(EMAIL, PASSWORD, NICKNAME));

        assertErrorCode(
            () -> authService.signup(new SignupRequest("PLAYER1@clutch.gg", PASSWORD, "player_two")),
            ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("[1.1.1] 이미 사용 중인 닉네임으로는 가입할 수 없다")
    void signup_duplicateNickname() {
        authService.signup(new SignupRequest(EMAIL, PASSWORD, NICKNAME));

        assertErrorCode(
            () -> authService.signup(new SignupRequest("player2@clutch.gg", PASSWORD, NICKNAME)),
            ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    @DisplayName("[1.1.1] 같은 이메일로 동시에 가입해도 한 명만 가입된다")
    void signup_concurrentSameEmail() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int n = i;
            executor.submit((Callable<Void>) () -> {
                ready.countDown();
                start.await(); // 모든 스레드가 준비된 뒤 동시에 출발
                try {
                    authService.signup(new SignupRequest("race@clutch.gg", PASSWORD, "racer_" + n));
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.DUPLICATE_EMAIL) {
                        duplicate.incrementAndGet();
                    } else {
                        unexpected.incrementAndGet();
                    }
                } catch (RuntimeException e) {
                    unexpected.incrementAndGet();
                }
                return null;
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(success.get()).isEqualTo(1);
        assertThat(duplicate.get()).isEqualTo(threads - 1);
        assertThat(unexpected.get()).isZero();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    // 로그인 테스트
    @Test
    @DisplayName("[1.1.1] 올바른 정보로 로그인하면 토큰이 발급된다 (이메일 대소문자 무시)")
    void login_success() {
        AuthResult signup = authService.signup(new SignupRequest(EMAIL, PASSWORD, NICKNAME));

        AuthResult login = authService.login(new LoginRequest("PLAYER1@CLUTCH.GG", PASSWORD));

        assertThat(login.getUser().getPublicId()).isEqualTo(signup.getUser().getPublicId());
        assertThat(login.getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("[1.1.1] 비밀번호가 틀리면 로그인에 실패한다")
    void login_wrongPassword() {
        authService.signup(new SignupRequest(EMAIL, PASSWORD, NICKNAME));

        assertErrorCode(
            () -> authService.login(new LoginRequest(EMAIL, "wrong1234")),
            ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("[1.1.1] 없는 이메일도 비밀번호 오류와 같은 에러로 응답한다 (가입 여부 노출 방지)")
    void login_unknownEmail() {
        assertErrorCode(
            () -> authService.login(new LoginRequest("nobody@clutch.gg", PASSWORD)),
            ErrorCode.INVALID_CREDENTIALS);
    }

    // 토큰 재발급 테스트
    @Test
    @DisplayName("[1.1.1] 재발급하면 새 리프레시 토큰이 나오고, 사용한 토큰은 다시 쓸 수 없다")
    void refresh_rotatesToken() {
        AuthResult signup = authService.signup(new SignupRequest(EMAIL, PASSWORD, NICKNAME));
        String oldRefreshToken = signup.getRefreshToken();

        AuthResult refreshed = authService.refresh(oldRefreshToken);

        assertThat(refreshed.getRefreshToken()).isNotEqualTo(oldRefreshToken);
        assertThat(refreshed.getUser().getPublicId()).isEqualTo(signup.getUser().getPublicId());
        assertErrorCode(() -> authService.refresh(oldRefreshToken), ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("[1.1.1] 같은 리프레시 토큰으로 동시에 재발급해도 한 번만 성공한다")
    void refresh_concurrentSameToken() throws Exception {
        String refreshToken = authService.signup(new SignupRequest(EMAIL, PASSWORD, NICKNAME)).getRefreshToken();

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit((Callable<Void>) () -> {
                ready.countDown();
                start.await();
                try {
                    authService.refresh(refreshToken);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    rejected.incrementAndGet();
                }
                return null;
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(success.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(threads - 1);
    }

    @Test
    @DisplayName("[1.1.1] 리프레시 토큰이 없거나 존재하지 않으면 재발급에 실패한다")
    void refresh_invalidToken() {
        assertErrorCode(() -> authService.refresh(null), ErrorCode.INVALID_REFRESH_TOKEN);
        assertErrorCode(() -> authService.refresh("not-issued-token"), ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // ---------- 로그아웃 ----------

    @Test
    @DisplayName("[1.1.1] 로그아웃하면 액세스 토큰과 리프레시 토큰을 모두 쓸 수 없다")
    void logout_invalidatesBothTokens() {
        AuthResult signup = authService.signup(new SignupRequest(EMAIL, PASSWORD, NICKNAME));
        Jwt accessToken = jwtDecoder.decode(signup.getAccessToken());

        authService.logout(accessToken.getId(), accessToken.getExpiresAt(), signup.getRefreshToken());

        assertThatThrownBy(() -> jwtDecoder.decode(signup.getAccessToken()))
            .isInstanceOf(JwtException.class);
        assertErrorCode(() -> authService.refresh(signup.getRefreshToken()), ErrorCode.INVALID_REFRESH_TOKEN);
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
