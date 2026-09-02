package com.allygo.allygo_api.auth.account.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.password")
public record PasswordProperties(int bcryptStrength) {
    public PasswordProperties {
        if (bcryptStrength < 4 || bcryptStrength > 31) {
            throw new IllegalArgumentException("auth.password.bcrypt-strength must be between 4 and 31");
        }
    }
}
