package com.allygo.allygo_api.auth.account.domain;

import java.time.Instant;

public final class AccountAuthException extends RuntimeException {
    public enum Reason {
        INVALID_SIGN_UP_REQUEST,
        INVALID_LOGIN_REQUEST,
        INVALID_LOGIN_ID_LOOKUP_REQUEST,
        INVALID_PASSWORD_RESET_REQUEST,
        INVALID_LOGIN_ID_FORMAT,
        INVALID_PASSWORD_FORMAT,
        PASSWORD_CONFIRM_MISMATCH,
        INVALID_NAME_FORMAT,
        INVALID_NICKNAME_FORMAT,
        INVALID_NATIONALITY_CODE,
        INVALID_POLICY_AGREEMENTS,
        REQUIRED_POLICY_NOT_AGREED,
        INVALID_DEVICE_ID,
        UNAUTHORIZED,
        USER_NOT_FOUND,
        INVALID_TOKEN_REFRESH_REQUEST,
        INVALID_LOGOUT_REQUEST,
        INVALID_ACCOUNT_WITHDRAWAL_REQUEST,
        INVALID_REFRESH_TOKEN_FORMAT,
        INVALID_REFRESH_TOKEN,
        REFRESH_TOKEN_EXPIRED,
        REFRESH_TOKEN_REVOKED,
        REFRESH_TOKEN_REUSE_DETECTED,
        REFRESH_TOKEN_OWNERSHIP_MISMATCH,
        INVALID_VERIFICATION_TOKEN,
        VERIFICATION_TOKEN_PURPOSE_MISMATCH,
        PHONE_VERIFICATION_NOT_FOUND,
        PHONE_NUMBER_NOT_REGISTERED,
        POLICY_DOCUMENT_NOT_FOUND,
        LOGIN_ID_ALREADY_EXISTS,
        NICKNAME_ALREADY_EXISTS,
        PHONE_NUMBER_ALREADY_REGISTERED,
        PHONE_VERIFICATION_ALREADY_CONSUMED,
        POLICY_DOCUMENT_NOT_EFFECTIVE,
        VERIFICATION_TOKEN_EXPIRED,
        INVALID_LOGIN_CREDENTIALS,
        ACCOUNT_SUSPENDED,
        ACCOUNT_BANNED,
        ACCOUNT_WITHDRAWN,
        PHONE_NUMBER_MISMATCH,
        LOGIN_RESTRICTED,
        ACTIVE_HELP_REQUEST_EXISTS,
        ACTIVE_HELP_SESSION_EXISTS,
        TOO_MANY_LOGIN_ATTEMPTS,
        TOO_MANY_TOKEN_REFRESH_ATTEMPTS
    }

    private final Reason reason;
    private final Integer retryAfter;
    private final Instant restrictionEndsAt;
    private final String activeResourceType;
    private final Long activeResourceId;
    private final String activeResourceStatus;

    private AccountAuthException(
            Reason reason,
            String message,
            Integer retryAfter,
            Instant restrictionEndsAt,
            String activeResourceType,
            Long activeResourceId,
            String activeResourceStatus
    ) {
        super(message);
        this.reason = reason;
        this.retryAfter = retryAfter;
        this.restrictionEndsAt = restrictionEndsAt;
        this.activeResourceType = activeResourceType;
        this.activeResourceId = activeResourceId;
        this.activeResourceStatus = activeResourceStatus;
    }

    public static AccountAuthException of(Reason reason, String message) {
        return new AccountAuthException(reason, message, null, null, null, null, null);
    }

    public static AccountAuthException tooManyAttempts(int retryAfter) {
        return new AccountAuthException(
                Reason.TOO_MANY_LOGIN_ATTEMPTS,
                "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                retryAfter,
                null,
                null,
                null,
                null
        );
    }

    public static AccountAuthException unauthorized() {
        return new AccountAuthException(
                Reason.UNAUTHORIZED,
                "인증이 필요합니다.",
                null,
                null,
                null,
                null,
                null
        );
    }

    public static AccountAuthException tooManyTokenRefreshAttempts(int retryAfter) {
        return new AccountAuthException(
                Reason.TOO_MANY_TOKEN_REFRESH_ATTEMPTS,
                "토큰 재발급 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                retryAfter,
                null,
                null,
                null,
                null
        );
    }

    public static AccountAuthException loginRestricted(Instant restrictionEndsAt) {
        return new AccountAuthException(
                Reason.LOGIN_RESTRICTED,
                "현재 계정은 로그인할 수 없습니다.",
                null,
                restrictionEndsAt,
                null,
                null,
                null
        );
    }

    public static AccountAuthException activeHelpRequest(long resourceId, String status) {
        return activeResource(
                Reason.ACTIVE_HELP_REQUEST_EXISTS,
                "종료되지 않은 도움 요청이 존재합니다.",
                "HELP_REQUEST",
                resourceId,
                status
        );
    }

    public static AccountAuthException activeHelpSession(long resourceId, String status) {
        return activeResource(
                Reason.ACTIVE_HELP_SESSION_EXISTS,
                "종료되지 않은 지원 세션이 존재합니다.",
                "HELP_SESSION",
                resourceId,
                status
        );
    }

    private static AccountAuthException activeResource(
            Reason reason,
            String message,
            String resourceType,
            long resourceId,
            String resourceStatus
    ) {
        return new AccountAuthException(
                reason, message, null, null, resourceType, resourceId, resourceStatus
        );
    }

    public Reason reason() {
        return reason;
    }

    public Integer retryAfter() {
        return retryAfter;
    }

    public Instant restrictionEndsAt() {
        return restrictionEndsAt;
    }

    public String activeResourceType() {
        return activeResourceType;
    }

    public Long activeResourceId() {
        return activeResourceId;
    }

    public String activeResourceStatus() {
        return activeResourceStatus;
    }
}
