package com.allygo.allygo_api.auth.account.presentation.response;

import com.allygo.allygo_api.auth.account.application.result.AccountWithdrawalResult;

import java.time.Instant;

public record AccountWithdrawalResponse(
        long withdrawalId,
        String accountStatus,
        Instant withdrawnAt,
        Instant retentionUntil
) {
    public static AccountWithdrawalResponse from(AccountWithdrawalResult result) {
        return new AccountWithdrawalResponse(
                result.withdrawalId(), result.accountStatus(), result.withdrawnAt(), result.retentionUntil()
        );
    }
}
