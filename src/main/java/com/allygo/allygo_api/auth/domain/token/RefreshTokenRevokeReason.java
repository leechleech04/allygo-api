package com.allygo.allygo_api.auth.domain.token;

public enum RefreshTokenRevokeReason {
    ROTATED,
    LOGOUT,
    REUSE_DETECTED,
    PASSWORD_RESET,
    ACCOUNT_WITHDRAWN,
    ADMIN_REVOKED
}
