package com.allygo.allygo_api.auth.phoneverification.application.port;

import java.util.Optional;

public interface UserPhonePort {
    boolean existsByPhoneNumber(String phoneE164);
    Optional<String> findPhoneNumberByUserId(Long userId);
}
