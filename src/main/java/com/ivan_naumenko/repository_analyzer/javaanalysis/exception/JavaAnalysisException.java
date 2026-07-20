package com.ivan_naumenko.repository_analyzer.javaanalysis.exception;

public class JavaAnalysisException extends RuntimeException {
    public JavaAnalysisException(String message, Exception e) {
        super(message, e);
    }
}
