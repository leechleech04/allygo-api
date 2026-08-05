package com.allygo.allygo_api.apprelease.domain;

public enum AppPlatform {
    IOS,
    ANDROID;

    public static AppPlatform from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("App platform is required.");
        }

        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported app platform: " + value, exception);
        }
    }
}
