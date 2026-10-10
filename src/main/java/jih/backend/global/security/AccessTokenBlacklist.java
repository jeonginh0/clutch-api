package jih.backend.global.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import jih.backend.global.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessTokenBlacklist {

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public void add(String jti, Instant expiresAt) {
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(RedisKeys.accessTokenBlacklist(jti), "1", remaining);
    }

    public boolean contains(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.accessTokenBlacklist(jti)));
    }
}
