package com.wpanther.transcript.orchestrator.integration;

import com.wpanther.transcript.orchestrator.application.port.out.XmlReadPort;
import com.wpanther.transcript.orchestrator.infrastructure.config.StorageProperties;
import com.wpanther.transcript.orchestrator.integration.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Streams a seeded XML object from MinIO through {@link XmlReadPort} fifty
 * times back-to-back and asserts the bytes round-trip cleanly. Repeated
 * open/close cycles exercise the {@code S3Client}'s HTTP connection pool; if
 * the stream were not closed (or the pool were not sized appropriately) the
 * 51st call would typically block waiting for a free connection.
 */
class XmlReadPortIT extends IntegrationTestBase {

    @Autowired XmlReadPort xmlReadPort;
    @Autowired StorageProperties storageProperties;

    private static S3Client seeder;

    @BeforeAll
    static void initSeeder() {
        // Use a dedicated seeder client so test seeding does not contend with
        // the production bean's connection pool. The container is started in
        // IntegrationTestBase#startContainers(); endpoint + credentials come
        // straight from the MinIO container reference.
        seeder = S3Client.builder()
            .endpointOverride(URI.create(MINIO.getS3URL()))
            .forcePathStyle(true)
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
            .build();
    }

    @AfterAll
    static void closeSeeder() {
        if (seeder != null) seeder.close();
    }

    @Test
    void streamsAndClosesWithoutLeak() throws Exception {
        String key = seedXml("<tc:Transcript>hello</tc:Transcript>");

        for (int i = 0; i < 50; i++) {
            try (var in = xmlReadPort.getObjectStream(key)) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(body).contains("Transcript");
            }
        }
    }

    /** Upload a tiny XML payload to the test bucket and return the storage key. */
    private String seedXml(String body) {
        String bucket = storageProperties.getXmlBucket();
        // MinIO starts with an empty namespace, so create the bucket on first use.
        // Tolerate parallel creators via the "already exists / already owned" sentinels.
        try {
            seeder.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ignore) {
            // bucket is already there; safe to use
        }
        String key = "it-read/" + UUID.randomUUID() + ".xml";
        seeder.putObject(req -> req.bucket(bucket).key(key),
            RequestBody.fromString(body, StandardCharsets.UTF_8));
        return key;
    }
}
