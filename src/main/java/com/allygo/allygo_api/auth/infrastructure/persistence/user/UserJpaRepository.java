package com.allygo.allygo_api.auth.infrastructure.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findByLoginIdIgnoreCase(String loginId);

    Optional<UserJpaEntity> findByPhoneE164(String phoneE164);

    boolean existsByLoginIdIgnoreCase(String loginId);

    boolean existsByPhoneE164(String phoneE164);
}
