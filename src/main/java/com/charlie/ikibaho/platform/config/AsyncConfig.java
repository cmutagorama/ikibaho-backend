package com.charlie.ikibaho.platform.config;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Async support, required for {@code @ApplicationModuleListener}.
 *
 * That annotation is meta-annotated {@code @Async}, and {@code @Async} without
 * {@code @EnableAsync} is not an error -- the method simply runs inline, on the
 * publisher's thread, inside the publisher's transaction. Everything still
 * appears to work while silently losing the isolation the whole design depends
 * on, so this file is load-bearing despite having no logic in it.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * A bounded queue on purpose. An unbounded one would absorb a backlog until
     * the heap gave out; bounded means a burst pushes back on the caller, which
     * is visible and recoverable.
     */
    @Bean
    TaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder
                .corePoolSize(4)
                .maxPoolSize(16)
                .queueCapacity(500)
                .threadNamePrefix("ikibaho-events-")
                .build();
    }
}
