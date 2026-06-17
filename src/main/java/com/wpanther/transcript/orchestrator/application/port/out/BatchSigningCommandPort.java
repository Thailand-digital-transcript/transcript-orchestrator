package com.wpanther.transcript.orchestrator.application.port.out;

import com.wpanther.transcript.orchestrator.domain.model.*;
import java.util.List;

public interface BatchSigningCommandPort {
    void sendBatchSigningCommand(Batch batch, List<TranscriptItem> items,
                                 SignerRole signerRole, SigningFormat format);
}
