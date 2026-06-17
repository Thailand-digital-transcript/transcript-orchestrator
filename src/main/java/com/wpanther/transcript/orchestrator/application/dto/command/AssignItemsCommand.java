package com.wpanther.transcript.orchestrator.application.dto.command;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.UUID;

@Getter @Setter
public class AssignItemsCommand {
    @NotEmpty private List<UUID> itemIds;
}
