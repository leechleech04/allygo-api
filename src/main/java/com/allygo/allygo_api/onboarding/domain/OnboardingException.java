package com.allygo.allygo_api.onboarding.domain;

import java.time.Instant;

public final class OnboardingException extends RuntimeException {
    public enum Reason {
        INVALID_INITIAL_SETTINGS_REQUEST,
        INVALID_NATIONALITY_CODE,
        INVALID_LANGUAGE_CODE,
        INVALID_TIMEZONE_NAME,
        INVALID_NOTIFICATION_PREFERENCES,
        INVALID_PERMISSION_STATUS_REQUEST,
        INVALID_PERMISSION_STATUS,
        INVALID_CHECKED_AT,
        USER_NOT_FOUND,
        LANGUAGE_NOT_FOUND,
        LANGUAGE_NOT_AVAILABLE,
        ONBOARDING_ALREADY_COMPLETED,
        ACCOUNT_SUSPENDED,
        ACCOUNT_BANNED,
        ACCOUNT_WITHDRAWN,
        LOGIN_RESTRICTED,
        STALE_PERMISSION_SNAPSHOT,
        PERMISSION_SNAPSHOT_TIMESTAMP_CONFLICT
    }

    private final Reason reason;
    private final Instant restrictionEndsAt;

    private OnboardingException(Reason reason, String message, Instant restrictionEndsAt) {
        super(message);
        this.reason = reason;
        this.restrictionEndsAt = restrictionEndsAt;
    }

    public static OnboardingException of(Reason reason, String message) {
        return new OnboardingException(reason, message, null);
    }

    public static OnboardingException loginRestricted(Instant restrictionEndsAt) {
        return new OnboardingException(
                Reason.LOGIN_RESTRICTED,
                "현재 계정은 로그인할 수 없습니다.",
                restrictionEndsAt
        );
    }

    public Reason reason() {
        return reason;
    }

    public Instant restrictionEndsAt() {
        return restrictionEndsAt;
    }
}
