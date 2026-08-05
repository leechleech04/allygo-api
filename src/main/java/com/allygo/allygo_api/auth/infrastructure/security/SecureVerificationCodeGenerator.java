package com.allygo.allygo_api.auth.infrastructure.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureVerificationCodeGenerator {

    private static final int CODE_BOUND = 1_000_000;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        return "%06d".formatted(secureRandom.nextInt(CODE_BOUND));
    }
}
