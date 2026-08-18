package com.allygo.allygo_api.auth.phoneverification.application;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class VerificationCodeGenerator {
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }
}
