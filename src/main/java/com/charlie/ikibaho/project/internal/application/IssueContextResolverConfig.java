package com.charlie.ikibaho.project.internal.application;

import com.charlie.ikibaho.project.IssueContextResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class IssueContextResolverConfig {
    @Bean
    @ConditionalOnMissingBean(IssueContextResolver.class)
    IssueContextResolver noOpIssueContextResolver() {
        return issueId -> Optional.empty();
    }
}
