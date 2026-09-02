package com.allygo.allygo_api.auth.account.infrastructure.security;

import com.allygo.allygo_api.auth.account.application.port.ProfileImageUrlPort;
import com.allygo.allygo_api.auth.phoneverification.infrastructure.security.JwtTokenProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Component
public class HmacProfileImageUrlAdapter implements ProfileImageUrlPort {
    private final ProfileImageProperties properties;
    private final byte[] key;

    public HmacProfileImageUrlAdapter(ProfileImageProperties properties, JwtTokenProperties tokenProperties) {
        this.properties = properties;
        this.key = Base64.getDecoder().decode(tokenProperties.secretBase64());
    }

    @Override
    public SignedImageUrl sign(String storageKey, Instant now) {
        Instant expiresAt = now.plus(properties.signedUrlTtl());
        String payload = storageKey + ':' + expiresAt.getEpochSecond();
        String signature = sign(payload);
        String encodedKey = URLEncoder.encode(storageKey, StandardCharsets.UTF_8).replace("+", "%20");
        String separator = properties.baseUrl().contains("?") ? "&" : "?";
        String url = properties.baseUrl() + separator + "key=" + encodedKey
                + "&expires=" + expiresAt.getEpochSecond() + "&signature=" + signature;
        return new SignedImageUrl(url, expiresAt);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign profile image URL", exception);
        }
    }
}
