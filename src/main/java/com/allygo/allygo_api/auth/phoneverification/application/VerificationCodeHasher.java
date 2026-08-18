package com.allygo.allygo_api.auth.phoneverification.application;

import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class VerificationCodeHasher {
    private final SecretKeySpec key;

    public VerificationCodeHasher(PhoneVerificationProperties properties) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.codePepperBase64());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("auth.verification.code-pepper-base64 must be valid Base64", exception);
        }
        if (decoded.length < 32) {
            throw new IllegalArgumentException("verification code pepper must be at least 256 bits");
        }
        this.key = new SecretKeySpec(decoded, "HmacSHA256");
    }

    public String hash(String phoneE164, VerificationPurpose purpose, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] value = (phoneE164 + ':' + purpose.name() + ':' + code).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(mac.doFinal(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash verification code", exception);
        }
    }
}
