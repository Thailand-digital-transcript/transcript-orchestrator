package com.wpanther.transcript.orchestrator.domain.model;
public enum BatchStatus {
    DRAFT, PENDING_REGISTRAR, REGISTRAR_SIGNING, PENDING_DEAN, DEAN_SIGNING,
    SEALING, PDF_GENERATION, PDF_SIGNING, COMPLETED, CANCELLED, FAILED;
    public boolean isHumanGate()  { return this == PENDING_REGISTRAR || this == PENDING_DEAN; }
    public boolean isAutomatic()  { return this == REGISTRAR_SIGNING || this == DEAN_SIGNING
        || this == SEALING || this == PDF_GENERATION || this == PDF_SIGNING; }
    public boolean isTerminal()   { return this == COMPLETED || this == CANCELLED || this == FAILED; }
}
