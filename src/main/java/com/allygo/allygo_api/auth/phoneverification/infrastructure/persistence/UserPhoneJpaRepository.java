package com.allygo.allygo_api.auth.phoneverification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface UserPhoneJpaRepository extends JpaRepository<UserPhoneJpaEntity, Long> {
    boolean existsByPhoneE164(String phoneE164);
}
