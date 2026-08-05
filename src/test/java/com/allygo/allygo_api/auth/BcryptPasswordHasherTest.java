package com.allygo.allygo_api.auth;

import com.allygo.allygo_api.auth.infrastructure.config.PasswordProperties;
import com.allygo.allygo_api.auth.infrastructure.security.BcryptPasswordHasher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BcryptPasswordHasherTest {

    private final BcryptPasswordHasher hasher = new BcryptPasswordHasher(new PasswordProperties(4));

    @Test
    void hashesWithSaltAndVerifiesWithoutExposingThePassword() {
        String first = hasher.hash("S3cure-password!");
        String second = hasher.hash("S3cure-password!");

        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain("S3cure-password!");
        assertThat(hasher.matches("S3cure-password!", first)).isTrue();
        assertThat(hasher.matches("wrong", first)).isFalse();
    }

    @Test
    void rejectsBlankPasswords() {
        assertThatThrownBy(() -> hasher.hash(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
