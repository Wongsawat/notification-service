package com.wpanther.notification.infrastructure.config;

import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async executor configuration.
 *
 * <p>Spring Boot's default {@code ThreadPoolTaskExecutor} uses {@code AbortPolicy}: when the
 * queue is full, rejected tasks throw {@code RejectedExecutionException} which is silently
 * swallowed in the scheduler catch blocks. This config replaces that with
 * {@code CallerRunsPolicy}: the calling (scheduler) thread executes the task itself, providing
 * natural back-pressure instead of silently dropping notifications.</p>
 */
@Configuration
public class AsyncConfig {

    @Bean
    public ThreadPoolTaskExecutorCustomizer asyncExecutorCustomizer() {
        return executor -> executor.setRejectedExecutionHandler(
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
