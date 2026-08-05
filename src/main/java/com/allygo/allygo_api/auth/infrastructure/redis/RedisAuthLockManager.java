package com.allygo.allygo_api.auth.infrastructure.redis;

import com.allygo.allygo_api.auth.application.port.TokenHasher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class RedisAuthLockManager {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final TokenHasher keyHasher;

    public RedisAuthLockManager(StringRedisTemplate redis, TokenHasher keyHasher) {
        this.redis = redis;
        this.keyHasher = keyHasher;
    }

    public <T> T execute(String namespace, String lockSubject, Duration lease, Supplier<T> action) {
        String key = "auth:lock:" + namespace + ':' + keyHasher.hash(lockSubject);
        String owner = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key, owner, lease);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new AuthLockUnavailableException(namespace);
        }
        try {
            return action.get();
        } finally {
            redis.execute(RELEASE_SCRIPT, List.of(key), owner);
        }
    }

    public static class AuthLockUnavailableException extends RuntimeException {
        public AuthLockUnavailableException(String namespace) {
            super("could not acquire auth lock: " + namespace);
        }
    }
}
