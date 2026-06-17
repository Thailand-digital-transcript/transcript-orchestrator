package com.wpanther.transcript.orchestrator.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateBatchCommand {
    @NotBlank private String name;
    @NotBlank private String institutionCode;
    @NotBlank private String createdBy;
}
