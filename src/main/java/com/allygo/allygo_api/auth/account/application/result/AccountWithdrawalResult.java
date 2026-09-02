package com.allygo.allygo_api.auth.account.application.result;

import java.time.Instant;

public record AccountWithdrawalResult(
        long withdrawalId,
        String accountStatus,
        Instant withdrawnAt,
        Instant retentionUntil
) {
}
