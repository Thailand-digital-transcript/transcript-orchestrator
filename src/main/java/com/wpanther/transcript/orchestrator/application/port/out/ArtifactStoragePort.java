package com.wpanther.transcript.orchestrator.application.port.out;

/**
 * Reclaims intermediate signing artifacts from object storage. Deletes are idempotent:
 * S3 (and MinIO) treat removing an absent key as success, so a retried sweep is harmless.
 */
public interface ArtifactStoragePort {
    void delete(String storageKey);
}
