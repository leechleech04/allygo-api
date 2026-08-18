package com.allygo.allygo_api.auth.phoneverification.application.port;

public interface SmsSenderPort {
    void sendVerificationCode(String phoneE164, String verificationCode, long expiresInSeconds);
}
