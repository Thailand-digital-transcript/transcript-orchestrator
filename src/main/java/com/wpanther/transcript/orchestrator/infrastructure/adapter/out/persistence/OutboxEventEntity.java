package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
public class OutboxEventEntity {

    @Id
    private UUID id;

    private String aggregateType;
    private String aggregateId;
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    private String status;
    private Integer retryCount;
    private String errorMessage;
    private String topic;
    private String partitionKey;

    @Column(columnDefinition = "TEXT")
    private String headers;

    private Instant createdAt;
    private Instant publishedAt;
}
