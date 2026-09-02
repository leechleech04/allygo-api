package com.allygo.allygo_api.auth.account.application.command;

import java.util.List;

public record SignUpCommand(
        String verificationToken,
        String loginId,
        String password,
        String passwordConfirm,
        String name,
        String nickname,
        String nationalityCode,
        List<PolicyAgreementCommand> policyAgreements,
        String deviceId,
        String ipAddress,
        String userAgent
) {
}
