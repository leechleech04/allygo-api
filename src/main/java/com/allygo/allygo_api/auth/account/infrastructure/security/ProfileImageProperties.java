package com.allygo.allygo_api.auth.account.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth.profile-image")
public record ProfileImageProperties(String baseUrl, Duration signedUrlTtl) {
    public ProfileImageProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("auth.profile-image.base-url is required");
        }
        if (signedUrlTtl == null || signedUrlTtl.isZero() || signedUrlTtl.isNegative()) {
            throw new IllegalArgumentException("auth.profile-image.signed-url-ttl must be positive");
        }
    }
}
