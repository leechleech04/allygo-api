package com.allygo.allygo_api.auth;

import com.allygo.allygo_api.auth.infrastructure.config.VerificationProperties;
import com.allygo.allygo_api.auth.infrastructure.security.HmacVerificationCodeHasher;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacVerificationCodeHasherTest {

    private final HmacVerificationCodeHasher hasher = new HmacVerificationCodeHasher(
            new VerificationProperties(
                    Duration.ofMinutes(3), Duration.ofMinutes(1),
                    5, 5, "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
            )
    );

    @Test
    void hashesSixDigitCodeWithServerSidePepper() {
        String hash = hasher.hash("004219");

        assertThat(hash).doesNotContain("004219");
        assertThat(hasher.matches("004219", hash)).isTrue();
        assertThat(hasher.matches("004218", hash)).isFalse();
    }

    @Test
    void rejectsInvalidCodeFormatBeforeHashing() {
        assertThatThrownBy(() -> hasher.hash("4219"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
