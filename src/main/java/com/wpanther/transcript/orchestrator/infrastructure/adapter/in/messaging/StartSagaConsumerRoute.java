package com.wpanther.transcript.orchestrator.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.transcript.orchestrator.application.dto.event.InboundStartSagaCommand;
import com.wpanther.transcript.orchestrator.application.usecase.RegisterTranscriptUseCase;
import com.wpanther.transcript.orchestrator.infrastructure.config.KafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class StartSagaConsumerRoute extends RouteBuilder {
    private final RegisterTranscriptUseCase useCase;
    private final ObjectMapper objectMapper;
    private final KafkaTopicProperties topics;

    @Value("${app.kafka.consumer.start-saga-group-id:transcript-orchestrator-start-saga}")
    private String groupId;

    @Override public void configure() {
        onException(Exception.class).handled(true)
            .log(LoggingLevel.ERROR, "StartSaga error: ${exception.message}")
            .to("kafka:" + topics.getDlq() + opts());

        from(url(topics.getStartSaga(), groupId))
            .routeId("start-saga-consumer")
            .process(ex -> {
                InboundStartSagaCommand cmd = objectMapper.readValue(
                    ex.getIn().getBody(String.class), InboundStartSagaCommand.class);
                useCase.register(cmd);
            });
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
