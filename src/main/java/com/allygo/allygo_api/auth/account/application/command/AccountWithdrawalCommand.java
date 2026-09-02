package com.allygo.allygo_api.auth.account.application.command;

public record AccountWithdrawalCommand(
        String verificationToken,
        String reasonCode,
        String reasonDetail,
        Boolean confirmed
) {
}
