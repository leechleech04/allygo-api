package com.allygo.allygo_api.auth.infrastructure.redis;

import com.allygo.allygo_api.auth.application.port.TokenHasher;
import com.allygo.allygo_api.auth.application.result.RateLimitDecision;
import com.allygo.allygo_api.auth.infrastructure.config.RateLimitProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class RedisAuthRateLimitStore {

    private static final DefaultRedisScript<Long> RECORD_FAILURE_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            if count >= tonumber(ARGV[2]) then
              redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final TokenHasher keyHasher;
    private final RateLimitProperties properties;

    public RedisAuthRateLimitStore(
            StringRedisTemplate redis,
            TokenHasher keyHasher,
            RateLimitProperties properties
    ) {
        this.redis = redis;
        this.keyHasher = keyHasher;
        this.properties = properties;
    }

    public RateLimitDecision checkLogin(String normalizedLoginId, String clientIp) {
        return check(blockKey("login", normalizedLoginId + '|' + clientIp));
    }

    public RateLimitDecision recordLoginFailure(String normalizedLoginId, String clientIp) {
        return record("login", normalizedLoginId + '|' + clientIp, properties.login());
    }

    public void clearLoginFailures(String normalizedLoginId, String clientIp) {
        clear("login", normalizedLoginId + '|' + clientIp);
    }

    public RateLimitDecision checkTokenRefresh(String clientIp) {
        return check(blockKey("refresh", clientIp));
    }

    public RateLimitDecision recordTokenRefreshFailure(String clientIp) {
        return record("refresh", clientIp, properties.tokenRefresh());
    }

    private RateLimitDecision record(String scope, String identifier, RateLimitProperties.Limit limit) {
        String counterKey = counterKey(scope, identifier);
        String blockKey = blockKey(scope, identifier);
        Long count = redis.execute(
                RECORD_FAILURE_SCRIPT,
                List.of(counterKey, blockKey),
                Long.toString(limit.window().toMillis()),
                Integer.toString(limit.maxAttempts()),
                Long.toString(limit.blockDuration().toMillis())
        );
        return count != null && count >= limit.maxAttempts()
                ? RateLimitDecision.deny(limit.blockDuration())
                : RateLimitDecision.allow();
    }

    private RateLimitDecision check(String blockKey) {
        Boolean blocked = redis.hasKey(blockKey);
        if (!Boolean.TRUE.equals(blocked)) {
            return RateLimitDecision.allow();
        }
        Long ttlMillis = redis.getExpire(blockKey, java.util.concurrent.TimeUnit.MILLISECONDS);
        return RateLimitDecision.deny(Duration.ofMillis(Math.max(0, ttlMillis == null ? 0 : ttlMillis)));
    }

    private void clear(String scope, String identifier) {
        redis.delete(List.of(counterKey(scope, identifier), blockKey(scope, identifier)));
    }

    private String counterKey(String scope, String identifier) {
        return "auth:rate:" + scope + ":count:" + keyHasher.hash(identifier);
    }

    private String blockKey(String scope, String identifier) {
        return "auth:rate:" + scope + ":block:" + keyHasher.hash(identifier);
    }
}
