package com.allygo.allygo_api.apprelease.infrastructure.persistence;

import com.allygo.allygo_api.apprelease.domain.AppPlatform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppReleasePolicyJpaRepository
        extends JpaRepository<AppReleasePolicyJpaEntity, Long> {

    List<AppReleasePolicyJpaEntity> findAllByPlatform(AppPlatform platform);
}
