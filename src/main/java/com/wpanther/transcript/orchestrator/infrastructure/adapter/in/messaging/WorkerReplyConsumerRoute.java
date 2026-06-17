package com.wpanther.transcript.orchestrator.infrastructure.adapter.in.messaging;

import static org.apache.camel.builder.Builder.simple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.transcript.orchestrator.application.dto.event.InboundBatchSigningReplyEvent;
import com.wpanther.transcript.orchestrator.application.dto.event.InboundPdfGenerationReplyEvent;
import com.wpanther.transcript.orchestrator.application.usecase.HandlePdfGenerationReplyUseCase;
import com.wpanther.transcript.orchestrator.application.usecase.HandleSigningReplyUseCase;
import com.wpanther.transcript.orchestrator.infrastructure.config.KafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Consumes worker reply events for two phases:
 * - signing replies (XAdES + PDF PAdES) from transcript-signing workers
 * - PDF generation replies from transcript-pdf-generation workers
 *
 * Each route deserializes the inbound DTO and hands it to the matching
 * use case. Any exception is logged and routed to the DLQ topic — the
 * message is not retried by this consumer (the outbox/saga layer handles
 * the retry/replay semantics via correlationId).
 */
@Component
@RequiredArgsConstructor
public class WorkerReplyConsumerRoute extends RouteBuilder {

    private final HandleSigningReplyUseCase signingReplyUseCase;
    private final HandlePdfGenerationReplyUseCase pdfReplyUseCase;
    private final ObjectMapper objectMapper;
    private final KafkaTopicProperties topics;

    @Value("${app.kafka.consumer.reply-group-id:transcript-orchestrator-reply}")
    private String groupId;

    @Override
    public void configure() {
        onException(Exception.class).handled(true)
            .log(LoggingLevel.ERROR, "Reply consumer error: ${exception.message}")
            // I3 fix: pass through the original Kafka message key so DLQ
            // consumers can correlate failures back to the batch / aggregate.
            .setHeader("kafka.KEY", simple("${headers[kafka.KEY]}"))
            .to("kafka:" + topics.getDlq() + opts());

        from(url(topics.getSigningReply(), groupId + "-signing"))
            .routeId("signing-reply-consumer")
            .process(ex -> signingReplyUseCase.handle(objectMapper.readValue(
                ex.getIn().getBody(String.class), InboundBatchSigningReplyEvent.class)));

        from(url(topics.getPdfGenerationReply(), groupId + "-pdf"))
            .routeId("pdf-generation-reply-consumer")
            .process(ex -> pdfReplyUseCase.handle(objectMapper.readValue(
                ex.getIn().getBody(String.class), InboundPdfGenerationReplyEvent.class)));
    }

    private String url(String topic, String group) {
        return "kafka:" + topic
            + "?brokers={{camel.component.kafka.brokers}}"
            + "&groupId=" + group
            + "&autoOffsetReset=earliest&autoCommitEnable=false"
            + "&breakOnFirstError=true&maxPollRecords=100"
            + "&pollTimeoutMs=5000&shutdownTimeout=5000&allowManualCommit=true";
    }

    private String opts() { return "?brokers={{camel.component.kafka.brokers}}"; }
}
