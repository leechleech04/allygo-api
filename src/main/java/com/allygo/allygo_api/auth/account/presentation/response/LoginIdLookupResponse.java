package com.allygo.allygo_api.auth.account.presentation.response;

import com.allygo.allygo_api.auth.account.application.result.LoginIdLookupResult;

public record LoginIdLookupResponse(String maskedLoginId) {
    public static LoginIdLookupResponse from(LoginIdLookupResult result) {
        return new LoginIdLookupResponse(result.maskedLoginId());
    }
}
