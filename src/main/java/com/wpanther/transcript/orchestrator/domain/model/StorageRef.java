package com.wpanther.transcript.orchestrator.domain.model;

/**
 * A bucket-qualified object reference. Never a URL — nothing anywhere sniffs the shape of
 * a string to decide what it holds. This is the type that travels from the orchestrator's
 * domain, through the outbox command adapter, across Kafka, into signing's storage adapter.
 */
public record StorageRef(String bucket, String key) {}
