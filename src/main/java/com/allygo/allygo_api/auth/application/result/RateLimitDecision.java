package com.allygo.allygo_api.auth.application.result;

import java.time.Duration;

public record RateLimitDecision(boolean allowed, Duration retryAfter) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, Duration.ZERO);
    }

    public static RateLimitDecision deny(Duration retryAfter) {
        return new RateLimitDecision(false, retryAfter.isNegative() ? Duration.ZERO : retryAfter);
    }
}
