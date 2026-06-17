package com.wpanther.transcript.orchestrator.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.transcript.orchestrator.application.dto.event.*;
import com.wpanther.transcript.orchestrator.application.usecase.*;
import com.wpanther.transcript.orchestrator.infrastructure.config.KafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class ApprovalConsumerRoute extends RouteBuilder {
    private final HandleRegistrarApprovalUseCase registrarUseCase;
    private final HandleDeanApprovalUseCase deanUseCase;
    private final ObjectMapper objectMapper;
    private final KafkaTopicProperties topics;
    @Value("${app.kafka.consumer.approval-group-id:transcript-orchestrator-approval}")
    private String groupId;

    @Override public void configure() {
        onException(Exception.class).handled(true)
            .log(LoggingLevel.ERROR, "Approval error: ${exception.message}")
            .to("kafka:" + topics.getDlq() + opts());

        from(url(topics.getApprovalRegistrar(), groupId + "-registrar"))
            .routeId("registrar-approval-consumer")
            .process(ex -> registrarUseCase.handle(
                objectMapper.readValue(ex.getIn().getBody(String.class), RegistrarApprovalEvent.class)));

        from(url(topics.getApprovalDean(), groupId + "-dean"))
            .routeId("dean-approval-consumer")
            .process(ex -> deanUseCase.handle(
                objectMapper.readValue(ex.getIn().getBody(String.class), DeanApprovalEvent.class)));
    }

    private String url(String topic, String group) {
        return "kafka:" + topic + "?brokers={{camel.component.kafka.brokers}}&groupId=" + group
            + "&autoOffsetReset=earliest&autoCommitEnable=false&breakOnFirstError=true"
            + "&maxPollRecords=100&pollTimeoutMs=5000&shutdownTimeout=5000&allowManualCommit=true";
    }
    private String opts() { return "?brokers={{camel.component.kafka.brokers}}"; }
}
