package com.allygo.allygo_api.apprelease.domain.exception;

import com.allygo.allygo_api.apprelease.domain.AppPlatform;

public final class AppReleasePolicyNotFoundException extends RuntimeException {

    public AppReleasePolicyNotFoundException(AppPlatform platform) {
        super("App release policy was not found for platform: " + platform);
    }
}
