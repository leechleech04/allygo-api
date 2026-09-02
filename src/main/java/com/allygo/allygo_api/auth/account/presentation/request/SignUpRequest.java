package com.allygo.allygo_api.auth.account.presentation.request;

import com.allygo.allygo_api.auth.account.application.command.SignUpCommand;

import java.util.List;

public record SignUpRequest(
        String verificationToken,
        String loginId,
        String password,
        String passwordConfirm,
        String name,
        String nickname,
        String nationalityCode,
        List<PolicyAgreementRequest> policyAgreements,
        String deviceId
) {
    public SignUpCommand toCommand(String ipAddress, String userAgent) {
        return new SignUpCommand(
                verificationToken, loginId, password, passwordConfirm, name, nickname,
                nationalityCode,
                policyAgreements == null ? null : policyAgreements.stream()
                        .map(item -> item == null ? null : item.toCommand())
                        .toList(),
                deviceId, ipAddress, userAgent
        );
    }
}
