package com.charlie.ikibaho.platform.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Background jobs, and the lock that keeps them singular.
 * <p>
 * Without ShedLock, every instance runs every scheduled job -- so a three-node
 * deployment sends each digest email three times and delivers each webhook three
 * times. The lock is what makes "run this hourly" mean hourly across the cluster
 * rather than hourly per process.
 * <p>
 * Note what is NOT locked: the SSE heartbeat. That one must run everywhere,
 * because each instance holds its own connections. See NotificationStream.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        // Lock timing comes from the database clock, not each JVM's.
                        // Two nodes whose clocks differ by a minute would otherwise
                        // both believe the other's lock had expired.
                        .usingDbTime()
                        .build());
    }
}
