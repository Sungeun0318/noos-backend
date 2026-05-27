package com.noos.backend.mobile.health;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class MobileHealthConfig {

    @Bean
    @Qualifier("mobileHealthRestTemplate")
    public RestTemplate mobileHealthRestTemplate(RestTemplateBuilder builder,
                                                 @Value("${noos.mobile.health.ace-step.timeout-ms:2000}") int timeoutMs) {
        Duration timeout = Duration.ofMillis(Math.max(100, timeoutMs));
        return builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }
}
