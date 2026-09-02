package com.allygo.allygo_api.auth.account.application.command;

public record LoginCommand(String loginId, String password, String deviceId, String ipAddress) {
}
