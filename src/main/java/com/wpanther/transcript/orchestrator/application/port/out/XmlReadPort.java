package com.wpanther.transcript.orchestrator.application.port.out;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * Streams the signed XML payload from S3/MinIO. The returned stream holds a
 * live HTTP connection from the underlying {@code S3Client}, so the caller
 * MUST close it (preferably via try-with-resources) to release the connection
 * back to the pool.
 */
public interface XmlReadPort {
    /** Open a stream to the object. Caller MUST close it (releases the HTTP connection). */
    ResponseInputStream<GetObjectResponse> getObjectStream(String storageKey);
}
