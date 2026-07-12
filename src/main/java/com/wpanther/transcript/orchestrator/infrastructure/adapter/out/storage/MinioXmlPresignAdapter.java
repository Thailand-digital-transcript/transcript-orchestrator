package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.storage;

import com.wpanther.transcript.orchestrator.application.port.out.ArtifactStoragePort;
import com.wpanther.transcript.orchestrator.application.port.out.XmlPresignPort;
import com.wpanther.transcript.orchestrator.application.port.out.XmlReadPort;
import com.wpanther.transcript.orchestrator.domain.model.StorageRef;
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
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Component @RequiredArgsConstructor
public class MinioXmlPresignAdapter implements XmlPresignPort, XmlReadPort, ArtifactStoragePort {
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

        // With three bucket properties instead of one, a typo would otherwise surface as a
        // 404 deep inside the registrar round, hours later. Fail at boot instead.
        for (String bucket : List.of(props.getOriginalBucket(), props.getSignedBucket(), props.getPdfBucket())) {
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (Exception e) {
                throw new IllegalStateException("Configured bucket is unreachable: " + bucket, e);
            }
        }
    }

    @PreDestroy void close() {
        // Both clients own an HTTP connection pool; close them so graceful
        // shutdown does not leak connections. Null-guards because @PreDestroy
        // can run before @PostConstruct on error paths.
        if (s3Client != null) s3Client.close();
        if (presigner != null) presigner.close();
    }

    @Override public String presign(StorageRef ref) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(props.getPresignDurationMinutes()))
            .getObjectRequest(GetObjectRequest.builder()
                .bucket(ref.bucket()).key(ref.key()).build())
            .build()).url().toString();
    }

    @Override
    public ResponseInputStream<GetObjectResponse> getObjectStream(StorageRef ref) {
        return s3Client.getObject(GetObjectRequest.builder()
            .bucket(ref.bucket()).key(ref.key()).build());
    }

    /**
     * Every ref the sweeper hands us — registrar/dean/sealed XML and the PAdES-signed PDF —
     * carries its own bucket, resolved by {@code TranscriptKeyResolver}. S3 deletes are
     * idempotent, so a re-run after a partial sweep is a no-op rather than an error.
     */
    @Override public void delete(StorageRef ref) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(ref.bucket()).key(ref.key()).build());
    }
}
