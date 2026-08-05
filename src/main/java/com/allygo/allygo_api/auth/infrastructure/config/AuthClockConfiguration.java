package com.allygo.allygo_api.auth.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AuthClockConfiguration {

    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }
}
