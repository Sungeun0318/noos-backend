package com.noos.backend.mobile.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("generationExecutor")
    public ThreadPoolTaskExecutor generationExecutor(
            @Value("${noos.mobile.session.queue-poolsize:4}") int pool,
            @Value("${noos.mobile.session.queue-capacity:64}") int capacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(pool);
        executor.setMaxPoolSize(pool);
        executor.setQueueCapacity(capacity);
        executor.setThreadNamePrefix("noos-gen-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}
