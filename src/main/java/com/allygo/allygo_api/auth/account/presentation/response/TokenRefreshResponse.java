package com.allygo.allygo_api.auth.account.presentation.response;

import com.allygo.allygo_api.auth.account.application.result.AuthTokenResult;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken,
        int accessTokenExpiresIn,
        int refreshTokenExpiresIn
) {
    public static TokenRefreshResponse from(AuthTokenResult result) {
        return new TokenRefreshResponse(
                result.accessToken(), result.refreshToken(),
                result.accessTokenExpiresIn(), result.refreshTokenExpiresIn()
        );
    }
}
