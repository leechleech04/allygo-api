package com.allygo.allygo_api.auth.account.application.command;

public record PasswordResetCommand(
        String verificationToken,
        String newPassword,
        String newPasswordConfirm
) {
}
