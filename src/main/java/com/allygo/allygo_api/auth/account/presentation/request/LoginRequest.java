package com.allygo.allygo_api.auth.account.presentation.request;

import com.allygo.allygo_api.auth.account.application.command.LoginCommand;

public record LoginRequest(String loginId, String password, String deviceId) {
    public LoginCommand toCommand(String ipAddress) {
        return new LoginCommand(loginId, password, deviceId, ipAddress);
    }
}
