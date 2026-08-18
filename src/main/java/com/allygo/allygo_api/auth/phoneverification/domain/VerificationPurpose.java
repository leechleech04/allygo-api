package com.allygo.allygo_api.auth.phoneverification.domain;

public enum VerificationPurpose {
    SIGN_UP,
    FIND_LOGIN_ID,
    RESET_PASSWORD,
    WITHDRAW_ACCOUNT;

    public static VerificationPurpose parse(String value) {
        if (value == null) {
            throw PhoneVerificationException.invalidPurpose();
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw PhoneVerificationException.invalidPurpose();
        }
    }

    public boolean requiresAuthentication() {
        return this == WITHDRAW_ACCOUNT;
    }

    public boolean requiresRegisteredPhone() {
        return this == FIND_LOGIN_ID || this == RESET_PASSWORD || this == WITHDRAW_ACCOUNT;
    }
}
