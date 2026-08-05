package com.allygo.allygo_api.auth.application.port;

public interface VerificationCodeHasher {

    String hash(String verificationCode);

    boolean matches(String verificationCode, String codeHash);
}
