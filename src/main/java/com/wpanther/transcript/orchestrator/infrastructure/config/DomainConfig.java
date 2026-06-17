package com.wpanther.transcript.orchestrator.infrastructure.config;

import com.wpanther.transcript.orchestrator.domain.service.BatchStateMachine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires pure domain services that are framework-agnostic into the Spring
 * container. {@link BatchStateMachine} has no dependencies, but it is not
 * annotated with {@code @Component} on purpose — the domain layer must stay
 * free of Spring annotations.
 */
@Configuration
public class DomainConfig {

    @Bean
    public BatchStateMachine batchStateMachine() {
        return new BatchStateMachine();
    }
}
