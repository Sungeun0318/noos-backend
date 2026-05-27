package com.noos.backend.mobile.config;

import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(PrometheusMeterRegistry.class)
public class MobilePrometheusConfig {

    @Bean
    @ConditionalOnMissingBean
    public PrometheusRegistry prometheusRegistry() {
        return new PrometheusRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public PrometheusMeterRegistry prometheusMeterRegistry(PrometheusRegistry prometheusRegistry, Clock clock) {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, prometheusRegistry, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public PrometheusScrapeEndpoint prometheusScrapeEndpoint(PrometheusRegistry prometheusRegistry) {
        return new PrometheusScrapeEndpoint(prometheusRegistry, PrometheusConfig.DEFAULT.prometheusProperties());
    }
}
