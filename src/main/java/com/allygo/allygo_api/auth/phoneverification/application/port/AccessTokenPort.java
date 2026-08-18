package com.allygo.allygo_api.auth.phoneverification.application.port;

public interface AccessTokenPort {
    Long requireUserId(String authorizationHeader);
}
