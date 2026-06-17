package com.wpanther.transcript.orchestrator.application.port.out;

import com.wpanther.transcript.orchestrator.domain.model.Batch;

public interface BatchCompletedEventPort {
    void publishBatchCompleted(Batch batch);
}
