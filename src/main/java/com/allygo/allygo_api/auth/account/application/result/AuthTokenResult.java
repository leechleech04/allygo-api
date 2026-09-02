package com.allygo.allygo_api.auth.account.application.result;

public record AuthTokenResult(
        String accessToken,
        String refreshToken,
        int accessTokenExpiresIn,
        int refreshTokenExpiresIn
) {
}
