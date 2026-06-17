package com.wpanther.transcript.orchestrator.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.kafka.topics")
@Getter
@Setter
public class KafkaTopicProperties {
    private String startSaga            = "saga.commands.orchestrator";
    private String approvalRegistrar    = "approval.registrar";
    private String approvalDean         = "approval.dean";
    private String signingReply         = "saga.reply.transcript-signing";
    private String pdfGenerationReply   = "saga.reply.transcript-pdf-generation";
    private String batchSigningCommand  = "saga.command.transcript-signing.batch";
    private String pdfGenerationCommand = "saga.command.transcript-pdf-generation";
    private String batchCompleted       = "transcript.batch.completed";
    private String dlq                  = "transcript.orchestrator.dlq";
}
