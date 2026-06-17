package com.wpanther.transcript.orchestrator.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.orchestrator")
@Getter
@Setter
public class OrchestratorProperties {
    private int stuckPhaseTimeoutMinutes = 10;
    private long sweeperIntervalMs       = 60_000L;
}
