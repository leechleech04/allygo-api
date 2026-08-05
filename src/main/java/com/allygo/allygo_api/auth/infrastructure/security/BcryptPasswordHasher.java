package com.allygo.allygo_api.auth.infrastructure.security;

import com.allygo.allygo_api.auth.application.port.PasswordHasher;
import com.allygo.allygo_api.auth.infrastructure.config.PasswordProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BcryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;

    public BcryptPasswordHasher(PasswordProperties properties) {
        this.encoder = new BCryptPasswordEncoder(properties.bcryptStrength());
    }

    @Override
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("rawPassword must not be blank");
        }
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return rawPassword != null
                && passwordHash != null
                && encoder.matches(rawPassword, passwordHash);
    }
}
