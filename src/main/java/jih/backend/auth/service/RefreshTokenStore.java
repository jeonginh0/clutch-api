package jih.backend.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import jih.backend.global.redis.RedisKeys;
import jih.backend.global.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public String issue(Long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        redisTemplate.opsForValue().set(key(token), String.valueOf(userId), jwtProperties.getRefreshTtl());
        return token;
    }

    public Optional<Long> consume(String token) {
        String userId = redisTemplate.opsForValue().getAndDelete(key(token));
        return Optional.ofNullable(userId).map(Long::valueOf);
    }

    public void revoke(String token) {
        redisTemplate.delete(key(token));
    }

    private String key(String token) {
        return RedisKeys.refreshToken(sha256(token));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
