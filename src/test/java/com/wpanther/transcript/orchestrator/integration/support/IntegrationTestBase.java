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
        registry.add("app.storage.xml-bucket", () -> "transcript-xmls");
    }
}
