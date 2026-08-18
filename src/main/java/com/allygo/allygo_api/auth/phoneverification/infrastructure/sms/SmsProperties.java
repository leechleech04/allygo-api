package com.allygo.allygo_api.auth.phoneverification.infrastructure.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "sms")
public record SmsProperties(
        URI baseUrl,
        String provider,
        String apiKey,
        String apiSecret,
        String senderNumber,
        Duration connectTimeout,
        Duration readTimeout
) {
    public SmsProperties {
        if (baseUrl == null || !"https".equalsIgnoreCase(baseUrl.getScheme())
                && !"http".equalsIgnoreCase(baseUrl.getScheme())) {
            throw new IllegalArgumentException("sms.base-url must be an HTTP(S) URL");
        }
        if (!"solapi".equalsIgnoreCase(provider)) {
            throw new IllegalArgumentException("sms.provider must be solapi");
        }
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()
                || senderNumber == null || senderNumber.isBlank()) {
            throw new IllegalArgumentException("SOLAPI credentials and sender number are required");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("SMS timeouts must be positive");
        }
    }
}
