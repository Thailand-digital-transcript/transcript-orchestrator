package com.wpanther.transcript.orchestrator.integration.support;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;
import java.util.List;

/**
 * Base for full-stack integration tests. Spins up PostgreSQL, Kafka, and MinIO
 * via Testcontainers and overrides the Spring properties (datasource URL, Camel
 * Kafka brokers, S3 endpoint/credentials/bucket) so the orchestrator points at
 * the containerised infrastructure.
 *
 * <p>Sub-classes get a {@link TestRestTemplate} bound to a random server port
 * and may construct a {@link KafkaTestHelper} from {@link #KAFKA}'s bootstrap
 * servers to send/receive messages directly to the test topics.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Import(TestJwtConfig.class)
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    public static final KafkaContainer KAFKA =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    public static final MinIOContainer MINIO =
        new MinIOContainer(DockerImageName.parse("minio/minio:latest"))
            .withUserName("minioadmin").withPassword("minioadmin");

    /**
     * The three buckets {@code MinioXmlPresignAdapter} headBuckets at {@code @PostConstruct}.
     * They MUST exist before the Spring context refreshes, not merely before the first test
     * method runs: unlike the old single-bucket setup (no boot-time check), a missing bucket
     * now fails context startup itself. Since {@code @DynamicPropertySource} methods run
     * during context preparation (before refresh) and are guaranteed to run after
     * {@link #startContainers()} (a static {@code @BeforeAll}, which precedes instance
     * creation and therefore precedes Spring's lazy context bootstrap on the first test
     * instance), creating the buckets here — rather than in each IT's own
     * {@code @BeforeEach}/first-use lazy-create, which now runs too late — is what keeps
     * every subclass's context boot from failing on the very first headBucket call.
     */
    public static final String ORIGINAL_BUCKET = "transcripts";
    public static final String SIGNED_BUCKET = "signed-transcripts";
    public static final String PDF_BUCKET = "transcript-pdfs";

    @Autowired protected TestRestTemplate restTemplate;

    /**
     * A bearer token for the live-HTTP ITs: a registrar scoped to institution
     * "KMUTT" (the institution every IT's batches use), so institution-scoped
     * reads (GET /api/v1/batches/{id}) succeed.
     */
    protected static String bearerToken() {
        return com.wpanther.transcript.orchestrator.integration.support.TestTokens
            .bearer(java.util.List.of("registrar"), "KMUTT", "e2e-it");
    }

    @BeforeAll
    static void startContainers() {
        POSTGRES.start();
        KAFKA.start();
        MINIO.start();
        createBuckets();
    }

    /**
     * Creates all three buckets against the raw MinIO container BEFORE any Spring context is
     * allowed to boot (see the field javadoc above for why timing matters here). Tolerates
     * parallel creators / re-creation across the several IT classes that each trigger their
     * own context build, via the "already exists / already owned" sentinels.
     */
    private static void createBuckets() {
        try (S3Client seeder = S3Client.builder()
                .endpointOverride(URI.create(MINIO.getS3URL()))
                .forcePathStyle(true)
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
                .build()) {
            for (String bucket : List.of(ORIGINAL_BUCKET, SIGNED_BUCKET, PDF_BUCKET)) {
                try {
                    seeder.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ignore) {
                    // bucket is already there; safe to use
                }
            }
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("camel.component.kafka.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.storage.endpoint", MINIO::getS3URL);
        registry.add("app.storage.access-key", () -> "minioadmin");
        registry.add("app.storage.secret-key", () -> "minioadmin");
        registry.add("app.storage.original-bucket", () -> ORIGINAL_BUCKET);
        registry.add("app.storage.signed-bucket", () -> SIGNED_BUCKET);
        registry.add("app.storage.pdf-bucket", () -> PDF_BUCKET);
    }
}
