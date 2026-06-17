package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.storage;

import com.wpanther.transcript.orchestrator.application.port.out.XmlPresignPort;
import com.wpanther.transcript.orchestrator.infrastructure.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import java.net.URI;
import java.time.Duration;

@Component @RequiredArgsConstructor
public class MinioXmlPresignAdapter implements XmlPresignPort {
    private final StorageProperties props;
    private S3Presigner presigner;

    @PostConstruct void init() {
        presigner = S3Presigner.builder()
            .endpointOverride(URI.create(props.getEndpoint()))
            .region(Region.of(props.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
            .build();
    }

    @Override public String presign(String storageKey) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(props.getPresignDurationMinutes()))
            .getObjectRequest(GetObjectRequest.builder()
                .bucket(props.getXmlBucket()).key(storageKey).build())
            .build()).url().toString();
    }
}
