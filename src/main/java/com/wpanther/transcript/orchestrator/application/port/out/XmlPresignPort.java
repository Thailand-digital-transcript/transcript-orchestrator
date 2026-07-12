package com.wpanther.transcript.orchestrator.application.port.out;

import com.wpanther.transcript.orchestrator.domain.model.StorageRef;

public interface XmlPresignPort {
    String presign(StorageRef ref);
}
