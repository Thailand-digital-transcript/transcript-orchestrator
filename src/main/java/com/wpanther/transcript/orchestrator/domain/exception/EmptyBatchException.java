package com.wpanther.transcript.orchestrator.domain.exception;
public class EmptyBatchException extends RuntimeException {
    public EmptyBatchException() { super("Cannot close an empty batch"); }
}
