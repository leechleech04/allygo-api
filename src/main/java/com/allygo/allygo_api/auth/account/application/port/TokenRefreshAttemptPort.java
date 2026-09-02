package com.allygo.allygo_api.auth.account.application.port;

public interface TokenRefreshAttemptPort {
    int blockedForSeconds(String ipAddress);

    int recordFailure(String ipAddress);
}
