package com.allygo.allygo_api.auth.account.infrastructure.ratelimit;

import com.allygo.allygo_api.auth.account.application.AccountAuthProperties;
import com.allygo.allygo_api.auth.account.application.port.LoginAttemptPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Component
public class RedisLoginAttemptAdapter implements LoginAttemptPort {
    private static final DefaultRedisScript<Long> RECORD_FAILURE = new DefaultRedisScript<>("""
            local blockTtl = redis.call('TTL', KEYS[2])
            if blockTtl > 0 then return blockTtl end
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            if count >= tonumber(ARGV[2]) then
              redis.call('SET', KEYS[2], '1', 'EX', ARGV[3])
              redis.call('DEL', KEYS[1])
              return tonumber(ARGV[3])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AccountAuthProperties properties;

    public RedisLoginAttemptAdapter(StringRedisTemplate redis, AccountAuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public int blockedForSeconds(String normalizedLoginId, String ipAddress) {
        Long ttl = redis.getExpire(blockKey(normalizedLoginId, ipAddress));
        return ttl == null || ttl <= 0 ? 0 : Math.toIntExact(ttl);
    }

    @Override
    public int recordFailure(String normalizedLoginId, String ipAddress) {
        String subject = subject(normalizedLoginId, ipAddress);
        Long result = redis.execute(
                RECORD_FAILURE,
                List.of("auth:login:fail:" + subject, "auth:login:block:" + subject),
                Long.toString(wholeSeconds(properties.window())),
                Integer.toString(properties.maxAttempts()),
                Long.toString(wholeSeconds(properties.blockDuration()))
        );
        return result == null ? 0 : Math.toIntExact(result);
    }

    @Override
    public void clear(String normalizedLoginId, String ipAddress) {
        String subject = subject(normalizedLoginId, ipAddress);
        redis.delete(List.of("auth:login:fail:" + subject, "auth:login:block:" + subject));
    }

    private static String blockKey(String loginId, String ipAddress) {
        return "auth:login:block:" + subject(loginId, ipAddress);
    }

    private static String subject(String loginId, String ipAddress) {
        String value = loginId + ':' + (ipAddress == null ? "unknown" : ipAddress);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static long wholeSeconds(Duration duration) {
        return Math.max(1, duration.toSeconds());
    }
}
