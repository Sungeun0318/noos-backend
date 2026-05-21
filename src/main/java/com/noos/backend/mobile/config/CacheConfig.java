package com.noos.backend.mobile.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.noos.backend.mobile.audio.dto.ResolvedAudio;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, ResolvedAudio> audioResolutionCache() {
        return Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofHours(1))
                .build();
    }
}
