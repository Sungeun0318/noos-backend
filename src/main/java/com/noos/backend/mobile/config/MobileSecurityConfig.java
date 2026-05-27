package com.noos.backend.mobile.config;

import com.noos.backend.mobile.auth.filter.JwtAuthenticationFilter;
import com.noos.backend.mobile.common.RateLimitFilter;
import com.noos.backend.mobile.common.RequestIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class MobileSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain mobileFilterChain(HttpSecurity http,
                                                 DeviceContextFilter deviceContextFilter,
                                                 RequestIdFilter requestIdFilter,
                                                 RateLimitFilter rateLimitFilter,
                                                 JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http
                .securityMatcher("/api/mobile/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/mobile/health").permitAll()
                        .requestMatchers(
                                "/api/mobile/auth/signup",
                                "/api/mobile/auth/login",
                                "/api/mobile/auth/refresh"
                        ).permitAll()
                        .requestMatchers("/api/mobile/**").permitAll()
                )
                .addFilterBefore(deviceContextFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(requestIdFilter, DeviceContextFilter.class)
                .addFilterBefore(rateLimitFilter, DeviceContextFilter.class)
                .addFilterAfter(jwtAuthenticationFilter, DeviceContextFilter.class);

        return http.build();
    }
}
