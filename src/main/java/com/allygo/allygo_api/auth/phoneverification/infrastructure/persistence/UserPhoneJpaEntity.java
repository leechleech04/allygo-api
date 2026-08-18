package com.allygo.allygo_api.auth.phoneverification.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
class UserPhoneJpaEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "phone_e164", nullable = false, length = 20)
    private String phoneE164;

    protected UserPhoneJpaEntity() {
    }

    String phoneE164() {
        return phoneE164;
    }
}
