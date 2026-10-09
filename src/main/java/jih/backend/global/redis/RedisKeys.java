package jih.backend.global.redis;

public final class RedisKeys {

    private static final String PREFIX = "clutch:";

    private RedisKeys() {
    }

    public static String refreshToken(String tokenHash) {
        return PREFIX + "auth:refresh:" + tokenHash;
    }

    public static String accessTokenBlacklist(String jtl) {
        return PREFIX + "auth:blacklist:" + jtl;
    }
}
