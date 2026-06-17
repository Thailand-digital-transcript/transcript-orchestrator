package com.wpanther.transcript.orchestrator.infrastructure.adapter.in.messaging;

import com.wpanther.saga.domain.outbox.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.List;

@Slf4j @Component
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxRelayRoute extends RouteBuilder {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxRepository;
    private final ProducerTemplate producerTemplate;  // Camel Spring Boot auto-configures one bean

    @Override public void configure() {
        from("timer:outbox-relay?period=5000&delay=2000")
            .routeId("outbox-relay")
            .process(this::drainPending)
            .split(body())
            .process(this::publishOne);
    }

    private void drainPending(Exchange exchange) {
        List<OutboxEvent> pending = outboxRepository.findPendingEvents(BATCH_SIZE);
        exchange.getIn().setBody(pending.isEmpty() ? List.of() : pending);
    }

    private void publishOne(Exchange exchange) {
        OutboxEvent event = exchange.getIn().getBody(OutboxEvent.class);
        if (event == null || event.getTopic() == null || event.getTopic().isBlank()) return;
        try {
            String endpoint = "kafka:" + event.getTopic()
                + "?brokers={{camel.component.kafka.brokers}}";
            String key = event.getPartitionKey() != null
                ? event.getPartitionKey() : event.getAggregateId();
            producerTemplate.sendBodyAndHeader(endpoint, event.getPayload(), "kafka.KEY", key);
            // Build updated event via builder if markAsPublished() is not available on OutboxEvent
            outboxRepository.save(OutboxEvent.builder()
                .id(event.getId()).aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId()).eventType(event.getEventType())
                .payload(event.getPayload()).topic(event.getTopic())
                .partitionKey(event.getPartitionKey()).headers(event.getHeaders())
                .createdAt(event.getCreatedAt()).publishedAt(java.time.Instant.now())
                .status(OutboxStatus.PUBLISHED).retryCount(event.getRetryCount()).build());
        } catch (Exception e) {
            log.error("Failed to relay outbox event {}: {}", event.getId(), e.getMessage(), e);
            outboxRepository.save(OutboxEvent.builder()
                .id(event.getId()).aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId()).eventType(event.getEventType())
                .payload(event.getPayload()).topic(event.getTopic())
                .partitionKey(event.getPartitionKey()).headers(event.getHeaders())
                .createdAt(event.getCreatedAt()).publishedAt(event.getPublishedAt())
                .status(OutboxStatus.FAILED).retryCount(event.getRetryCount() + 1)
                .errorMessage(e.getMessage()).build());
        }
    }
}
