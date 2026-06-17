package com.wpanther.transcript.orchestrator.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.storage")
@Getter
@Setter
public class StorageProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String region          = "us-east-1";
    private String xmlBucket;
    private boolean pathStyleAccess = true;
    private int presignDurationMinutes = 60;
}
