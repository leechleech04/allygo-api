package com.allygo.allygo_api.apprelease.application;

import com.allygo.allygo_api.apprelease.application.port.AppReleasePolicyQueryPort;
import com.allygo.allygo_api.apprelease.application.result.AppReleasePolicyResult;
import com.allygo.allygo_api.apprelease.domain.AppPlatform;
import com.allygo.allygo_api.apprelease.domain.AppReleasePolicy;
import com.allygo.allygo_api.apprelease.domain.exception.AppReleasePolicyNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AppReleasePolicyQueryService {

    private final AppReleasePolicyQueryPort queryPort;

    public AppReleasePolicyQueryService(AppReleasePolicyQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    public AppReleasePolicyResult getPolicy(AppPlatform platform) {
        List<AppReleasePolicy> policies = queryPort.findAllByPlatform(platform);

        if (policies.isEmpty()) {
            throw new AppReleasePolicyNotFoundException(platform);
        }
        if (policies.size() > 1) {
            throw new IllegalStateException("Multiple app release policies found for platform: " + platform);
        }

        return AppReleasePolicyResult.from(policies.getFirst());
    }
}
