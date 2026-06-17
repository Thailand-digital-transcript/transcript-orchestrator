package com.wpanther.transcript.orchestrator.application.port.out;

import com.wpanther.transcript.orchestrator.domain.model.*;
import java.util.List;

public interface PdfGenerationCommandPort {
    void sendPdfGenerationCommand(Batch batch, List<TranscriptItem> items);
}
