package com.wpanther.transcript.orchestrator.integration;

import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import com.wpanther.transcript.orchestrator.domain.service.TranscriptKeyResolver;
import com.wpanther.transcript.orchestrator.integration.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The integration half of the confirmed PDF_RENDERED bug (the domain rule is pinned by
 * {@code TranscriptItemTest#atPdfRendered_theSignerWantsThePdf_butTheViewerWantsTheSealedXml}).
 * Before the Task 5 bucket split, {@code /content} at PDF_RENDERED fed a <em>presigned
 * URL</em> to {@code getObjectStream()} as though it were an object key, against the XML
 * bucket. This drives the endpoint against a real MinIO and proves it streams the sealed XML
 * from signed-transcripts, not the rendered PDF and not a 404.
 */
class TranscriptItemContentIT extends IntegrationTestBase {

    @Autowired TranscriptItemRepository items;
    @Autowired TranscriptKeyResolver resolver;

    private static S3Client seeder;

    @BeforeAll
    static void initSeeder() {
        seeder = S3Client.builder()
            .endpointOverride(URI.create(MINIO.getS3URL()))
            .forcePathStyle(true)
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
            .build();
    }

    @Test
    void contentEndpoint_atPdfRendered_streamsTheSealedXml_notThePdf() {
        String orig = "2026/07/10/01/transcript-" + UUID.randomUUID() + ".xml";

        // Put a real sealed XML in signed-transcripts, at the key the resolver derives.
        var sealedRef = resolver.sealed(orig);
        seeder.putObject(req -> req.bucket(sealedRef.bucket()).key(sealedRef.key()),
            RequestBody.fromString("<Transcript/>", StandardCharsets.UTF_8));

        // Drive an item to PDF_RENDERED. The literal values passed to markX are presence
        // flags only (Task 4) — the actual keys read back are always resolver-derived from
        // `orig` — but they must be non-null to select the right branch of latestXmlRef.
        TranscriptItem item = TranscriptItem.register(
            "tx-" + UUID.randomUUID(), "doc-" + UUID.randomUUID(), "KMUTT", "01", orig);
        item.markRegistrarSigned("registrar-placeholder");
        item.markDeanSigned("dean-placeholder");
        item.markSealed("sealed-placeholder");
        item.markPdfRendered("pdf-placeholder");
        items.save(item);

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(bearerToken());
        ResponseEntity<String> res = restTemplate.exchange(
            "/api/v1/transcripts/" + item.getId() + "/content",
            HttpMethod.GET, new HttpEntity<>(h), String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        // XML, not a 404 and not PDF bytes.
        assertThat(res.getBody()).startsWith("<");
    }
}
