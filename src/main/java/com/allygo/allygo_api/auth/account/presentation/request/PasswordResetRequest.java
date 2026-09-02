package com.allygo.allygo_api.auth.account.presentation.request;

import com.allygo.allygo_api.auth.account.application.command.PasswordResetCommand;

public record PasswordResetRequest(
        String verificationToken,
        String newPassword,
        String newPasswordConfirm
) {
    public PasswordResetCommand toCommand() {
        return new PasswordResetCommand(verificationToken, newPassword, newPasswordConfirm);
    }
}
