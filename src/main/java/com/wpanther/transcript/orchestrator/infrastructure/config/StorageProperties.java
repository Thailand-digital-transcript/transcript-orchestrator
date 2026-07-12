package com.wpanther.transcript.orchestrator.infrastructure.config;

import com.wpanther.transcript.orchestrator.domain.service.TranscriptKeyResolver;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.storage")
@Getter
@Setter
public class StorageProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String region          = "us-east-1";
    private String originalBucket;   // transcripts        — the submitted XML
    private String signedBucket;     // signed-transcripts — every signed artifact
    private String pdfBucket;        // transcript-pdfs    — the unsigned rendered PDF
    private boolean pathStyleAccess = true;
    private int presignDurationMinutes = 60;

    @Bean
    public TranscriptKeyResolver transcriptKeyResolver() {
        return new TranscriptKeyResolver(originalBucket, signedBucket, pdfBucket);
    }
}
