package com.allygo.allygo_api.auth.phoneverification.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.allygo.allygo_api.auth.phoneverification.infrastructure.security.JwtTokenProperties;
import com.allygo.allygo_api.auth.phoneverification.infrastructure.sms.SmsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({
        PhoneVerificationProperties.class,
        JwtTokenProperties.class,
        SmsProperties.class
})
public class PhoneVerificationConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
