package com.allygo.allygo_api.auth.phoneverification.infrastructure.persistence;

import com.allygo.allygo_api.auth.phoneverification.application.port.UserPhonePort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserPhonePersistenceAdapter implements UserPhonePort {
    private final UserPhoneJpaRepository repository;

    public UserPhonePersistenceAdapter(UserPhoneJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByPhoneNumber(String phoneE164) {
        return repository.existsByPhoneE164(phoneE164);
    }

    @Override
    public Optional<String> findPhoneNumberByUserId(Long userId) {
        return repository.findById(userId).map(UserPhoneJpaEntity::phoneE164);
    }
}
