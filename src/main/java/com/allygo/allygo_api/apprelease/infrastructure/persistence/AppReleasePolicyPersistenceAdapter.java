package com.allygo.allygo_api.apprelease.infrastructure.persistence;

import com.allygo.allygo_api.apprelease.application.port.AppReleasePolicyQueryPort;
import com.allygo.allygo_api.apprelease.domain.AppPlatform;
import com.allygo.allygo_api.apprelease.domain.AppReleasePolicy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AppReleasePolicyPersistenceAdapter implements AppReleasePolicyQueryPort {

    private final AppReleasePolicyJpaRepository repository;

    public AppReleasePolicyPersistenceAdapter(AppReleasePolicyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AppReleasePolicy> findAllByPlatform(AppPlatform platform) {
        return repository.findAllByPlatform(platform).stream()
                .map(AppReleasePolicyJpaEntity::toDomain)
                .toList();
    }
}
