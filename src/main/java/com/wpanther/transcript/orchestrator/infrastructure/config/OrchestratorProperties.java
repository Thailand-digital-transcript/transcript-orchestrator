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

    /**
     * Grace period before a dead item's intermediate artifacts are deleted. Deletion is
     * permanent, so this is deliberately generous: it leaves an operator a week to inspect
     * the artifacts of a fresh failure while diagnosing it.
     */
    private int orphanRetentionHours     = 168;   // 7 days
    private long orphanSweeperIntervalMs = 3_600_000L;  // hourly
    private int orphanSweepBatchSize     = 100;
}
