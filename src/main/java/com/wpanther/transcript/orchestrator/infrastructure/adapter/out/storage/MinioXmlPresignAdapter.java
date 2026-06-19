package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.storage;

import com.wpanther.transcript.orchestrator.application.port.out.XmlPresignPort;
import com.wpanther.transcript.orchestrator.application.port.out.XmlReadPort;
import com.wpanther.transcript.orchestrator.infrastructure.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

@Component @RequiredArgsConstructor
public class MinioXmlPresignAdapter implements XmlPresignPort, XmlReadPort {
    private final StorageProperties props;
    private S3Presigner presigner;
    private S3Client s3Client;

    @PostConstruct void init() {
        var creds = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey()));
        presigner = S3Presigner.builder()
            .endpointOverride(URI.create(props.getEndpoint()))
            .region(Region.of(props.getRegion()))
            .credentialsProvider(creds)
            .build();
        s3Client = S3Client.builder()
            .endpointOverride(URI.create(props.getEndpoint()))
            .forcePathStyle(true)                       // MinIO requires path-style addressing
            .region(Region.of(props.getRegion()))
            .credentialsProvider(creds)
            .build();
    }

    @PreDestroy void close() {
        // Both clients own an HTTP connection pool; close them so graceful
        // shutdown does not leak connections. Null-guards because @PreDestroy
        // can run before @PostConstruct on error paths.
        if (s3Client != null) s3Client.close();
        if (presigner != null) presigner.close();
    }

    @Override public String presign(String storageKey) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(props.getPresignDurationMinutes()))
            .getObjectRequest(GetObjectRequest.builder()
                .bucket(props.getXmlBucket()).key(storageKey).build())
            .build()).url().toString();
    }

    @Override
    public ResponseInputStream<GetObjectResponse> getObjectStream(String storageKey) {
        return s3Client.getObject(GetObjectRequest.builder()
            .bucket(props.getXmlBucket()).key(storageKey).build());
    }
}
