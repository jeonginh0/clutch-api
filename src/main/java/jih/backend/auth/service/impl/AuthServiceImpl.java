package jih.backend.auth.service.impl;

import java.time.Instant;
import java.util.Locale;
import jih.backend.auth.dto.AuthUserResponse;
import jih.backend.auth.dto.LoginRequest;
import jih.backend.auth.dto.SignupRequest;
import jih.backend.auth.service.AuthResult;
import jih.backend.auth.service.AuthService;
import jih.backend.auth.service.RefreshTokenStore;
import jih.backend.global.error.BusinessException;
import jih.backend.global.error.ErrorCode;
import jih.backend.global.security.AccessTokenBlacklist;
import jih.backend.global.security.JwtProperties;
import jih.backend.global.security.JwtProvider;
import jih.backend.user.domain.User;
import jih.backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final String EMAIL_UNIQUE_INDEX = "uq_users_email_active";
    private static final String NICKNAME_UNIQUE_INDEX = "uq_users_nickname_active";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenStore refreshTokenStore;
    private final AccessTokenBlacklist accessTokenBlacklist;
    private final String dummyPasswordHash;

    public AuthServiceImpl(UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtProvider jwtProvider,
        JwtProperties jwtProperties,
        RefreshTokenStore refreshTokenStore,
        AccessTokenBlacklist accessTokenBlacklist) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
        this.refreshTokenStore = refreshTokenStore;
        this.accessTokenBlacklist = accessTokenBlacklist;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Override
    @Transactional
    public AuthResult signup(SignupRequest request) {
        String email = normalizeEmail(request.getEmail());
        String nickname = request.getNickname().strip();

        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNicknameAndDeletedAtIsNull(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        User user = User.create(email, passwordEncoder.encode(request.getPassword()), nickname);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw toDuplicateException(e);
        }

        log.info("signup completed. userId={}", user.getId());
        return issueTokens(user);
    }

    @Override
    public AuthResult login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.getPassword(), dummyPasswordHash);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        log.info("login succeeded. userId={}", user.getId());
        return issueTokens(user);
    }

    @Override
    public AuthResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        Long userId = refreshTokenStore.consume(refreshToken)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        return issueTokens(user);
    }

    @Override
    public void logout(String accessTokenId, Instant accessTokenExpiresAt, String refreshToken) {
        accessTokenBlacklist.add(accessTokenId, accessTokenExpiresAt);
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenStore.revoke(refreshToken);
        }
    }

    private AuthResult issueTokens(User user) {
        return new AuthResult(
            jwtProvider.issueAccessToken(user),
            jwtProvider.getAccessTokenExpiresInSeconds(),
            refreshTokenStore.issue(user.getId()),
            jwtProperties.getRefreshTtl(),
            AuthUserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static BusinessException toDuplicateException(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        if (message.contains(EMAIL_UNIQUE_INDEX)) {
            return new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (message.contains(NICKNAME_UNIQUE_INDEX)) {
            return new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        throw e;
    }
}
