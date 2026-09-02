package com.allygo.allygo_api.auth.account.application;

import com.allygo.allygo_api.auth.account.infrastructure.security.ProfileImageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties({
        AccountAuthProperties.class,
        TokenRefreshRateLimitProperties.class,
        PasswordProperties.class,
        ProfileImageProperties.class
})
public class AccountAuthConfiguration {
    @Bean
    PasswordEncoder passwordEncoder(PasswordProperties properties) {
        return new BCryptPasswordEncoder(properties.bcryptStrength());
    }
}
