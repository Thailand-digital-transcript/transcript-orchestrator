package com.wpanther.transcript.orchestrator.infrastructure.adapter.in.messaging;

import com.wpanther.saga.domain.outbox.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Slf4j @Component
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxRelayRoute extends RouteBuilder {

    private static final int BATCH_SIZE = 100;
    // I5 fix: FAILED outbox events are now retried on every tick. Once an event
    // exceeds MAX_RETRIES attempts it stays FAILED for manual intervention
    // (no terminal DEAD state in OutboxStatus yet) and a final log line is emitted.
    private static final int MAX_RETRIES = 5;

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
        // I5 fix: pick up FAILED events in addition to PENDING. Failed events
        // with retryCount >= MAX_RETRIES are skipped (no point hammering on a
        // permanently broken payload). Slot the remaining capacity with failed
        // events before exhausting the PENDING batch.
        List<OutboxEvent> pending = outboxRepository.findPendingEvents(BATCH_SIZE);
        int remaining = Math.max(0, BATCH_SIZE - pending.size());
        List<OutboxEvent> failed = remaining > 0
            ? outboxRepository.findFailedEvents(remaining) : List.of();
        List<OutboxEvent> retryable = failed.stream()
            .filter(e -> e.getRetryCount() < MAX_RETRIES)
            .toList();
        List<OutboxEvent> all = new ArrayList<>(pending.size() + retryable.size());
        all.addAll(pending);
        all.addAll(retryable);
        exchange.getIn().setBody(all.isEmpty() ? List.of() : all);
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
            int newRetryCount = event.getRetryCount() + 1;
            log.error("Failed to relay outbox event {} (attempt {}/{}): {}",
                event.getId(), newRetryCount, MAX_RETRIES, e.getMessage(), e);
            outboxRepository.save(OutboxEvent.builder()
                .id(event.getId()).aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId()).eventType(event.getEventType())
                .payload(event.getPayload()).topic(event.getTopic())
                .partitionKey(event.getPartitionKey()).headers(event.getHeaders())
                .createdAt(event.getCreatedAt()).publishedAt(event.getPublishedAt())
                .status(OutboxStatus.FAILED).retryCount(newRetryCount)
                .errorMessage(e.getMessage()).build());
            if (newRetryCount >= MAX_RETRIES) {
                // I5 fix: surface permanently-failed events with a distinct
                // ERROR log so operators can spot them in log-aggregation tools.
                // OutboxStatus has no DEAD state, so FAILED + retryCount >= MAX
                // is the operational signal that manual intervention is required.
                log.error("Outbox event {} exceeded max retries ({}); manual intervention required",
                    event.getId(), MAX_RETRIES);
            }
        }
    }
}
