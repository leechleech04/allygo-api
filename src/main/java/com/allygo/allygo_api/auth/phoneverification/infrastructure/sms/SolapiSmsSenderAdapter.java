package com.allygo.allygo_api.auth.phoneverification.infrastructure.sms;

import com.allygo.allygo_api.auth.phoneverification.application.port.SmsSenderPort;
import com.allygo.allygo_api.auth.phoneverification.domain.PhoneVerificationException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Component
public class SolapiSmsSenderAdapter implements SmsSenderPort {
    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    private final SmsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final SecureRandom secureRandom = new SecureRandom();

    public SolapiSmsSenderAdapter(SmsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    @Override
    public void sendVerificationCode(String phoneE164, String verificationCode, long expiresInSeconds) {
        HttpRequest request = HttpRequest.newBuilder(resolveSendUri())
                .timeout(properties.readTimeout())
                .header("Authorization", authorizationHeader())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(phoneE164, verificationCode, expiresInSeconds)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400 && response.statusCode() < 500) {
                throw PhoneVerificationException.smsSendFailed();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw PhoneVerificationException.smsUnavailable();
            }
            JsonNode failures = objectMapper.readTree(response.body()).path("failedMessageList");
            if (!failures.isArray() || !failures.isEmpty()) {
                throw PhoneVerificationException.smsSendFailed();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw PhoneVerificationException.smsUnavailable();
        } catch (IOException exception) {
            throw PhoneVerificationException.smsUnavailable();
        }
    }

    private URI resolveSendUri() {
        String base = properties.baseUrl().toString();
        return URI.create((base.endsWith("/") ? base : base + "/") + "messages/v4/send-many/detail");
    }

    private String authorizationHeader() {
        String date = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        byte[] saltBytes = new byte[16];
        secureRandom.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        return "HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s".formatted(
                properties.apiKey(), date, salt, hmacSha256(date + salt)
        );
    }

    private String hmacSha256(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.apiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create SOLAPI authorization signature", exception);
        }
    }

    private String requestBody(String phoneE164, String verificationCode, long expiresInSeconds) {
        try {
            var parsed = PHONE_NUMBER_UTIL.parse(phoneE164, "ZZ");
            String recipient = PHONE_NUMBER_UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
                    .replaceAll("\\D", "");
            Map<String, Object> message = Map.of(
                    "to", recipient,
                    "country", Integer.toString(parsed.getCountryCode()),
                    "from", properties.senderNumber().replaceAll("\\D", ""),
                    "type", "SMS",
                    "text", "[AllyGo] 인증번호는 " + verificationCode + "입니다. "
                            + expiresInSeconds + "초 안에 입력해 주세요."
            );
            return objectMapper.writeValueAsString(Map.of(
                    "messages", List.of(message),
                    "strict", true,
                    "showMessageList", true
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create SOLAPI request", exception);
        }
    }
}
