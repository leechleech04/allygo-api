package com.allygo.allygo_api.auth.account.application.port;

import java.time.Instant;

public interface ProfileImageUrlPort {
    SignedImageUrl sign(String storageKey, Instant now);

    record SignedImageUrl(String url, Instant expiresAt) {
    }
}
