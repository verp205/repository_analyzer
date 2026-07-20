package com.ivan_naumenko.repository_analyzer.llm.context.exception;

public class ContextSerializationException extends RuntimeException {
    public ContextSerializationException(String message, Exception e) {
        super(message, e);
    }
}
