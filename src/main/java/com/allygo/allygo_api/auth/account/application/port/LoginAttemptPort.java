package com.allygo.allygo_api.auth.account.application.port;

public interface LoginAttemptPort {
    int blockedForSeconds(String normalizedLoginId, String ipAddress);

    int recordFailure(String normalizedLoginId, String ipAddress);

    void clear(String normalizedLoginId, String ipAddress);
}
