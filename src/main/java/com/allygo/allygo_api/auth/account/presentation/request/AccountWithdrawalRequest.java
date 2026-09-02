package com.allygo.allygo_api.auth.account.presentation.request;

import com.allygo.allygo_api.auth.account.application.command.AccountWithdrawalCommand;

public record AccountWithdrawalRequest(
        String verificationToken,
        String reasonCode,
        String reasonDetail,
        Boolean confirmed
) {
    public AccountWithdrawalCommand toCommand() {
        return new AccountWithdrawalCommand(verificationToken, reasonCode, reasonDetail, confirmed);
    }
}
