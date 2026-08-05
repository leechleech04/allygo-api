package com.allygo.allygo_api.auth.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth.password")
public record PasswordProperties(
        @Min(4) @Max(31) int bcryptStrength
) {
}
