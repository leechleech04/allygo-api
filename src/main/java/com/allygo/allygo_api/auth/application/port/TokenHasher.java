package com.allygo.allygo_api.auth.application.port;

public interface TokenHasher {

    String hash(String rawToken);
}
