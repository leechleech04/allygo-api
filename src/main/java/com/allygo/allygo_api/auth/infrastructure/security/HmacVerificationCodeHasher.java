package com.allygo.allygo_api.auth.infrastructure.security;

import com.allygo.allygo_api.auth.application.port.VerificationCodeHasher;
import com.allygo.allygo_api.auth.infrastructure.config.VerificationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class HmacVerificationCodeHasher implements VerificationCodeHasher {

    private final byte[] pepper;

    public HmacVerificationCodeHasher(VerificationProperties properties) {
        try {
            this.pepper = Base64.getDecoder().decode(properties.codePepperBase64());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("verification code pepper must be valid Base64", exception);
        }
        if (pepper.length < 32) {
            throw new IllegalStateException("verification code pepper must contain at least 32 bytes");
        }
    }

    @Override
    public String hash(String verificationCode) {
        validate(verificationCode);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(verificationCode.getBytes(StandardCharsets.US_ASCII))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("could not hash verification code", exception);
        }
    }

    @Override
    public boolean matches(String verificationCode, String codeHash) {
        if (verificationCode == null || codeHash == null || !verificationCode.matches("\\d{6}")) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(verificationCode).getBytes(StandardCharsets.US_ASCII),
                codeHash.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static void validate(String verificationCode) {
        if (verificationCode == null || !verificationCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("verificationCode must contain exactly 6 digits");
        }
    }
}
