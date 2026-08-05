package com.allygo.allygo_api.apprelease.application.port;

import com.allygo.allygo_api.apprelease.domain.AppPlatform;
import com.allygo.allygo_api.apprelease.domain.AppReleasePolicy;

import java.util.List;

public interface AppReleasePolicyQueryPort {

    List<AppReleasePolicy> findAllByPlatform(AppPlatform platform);
}
