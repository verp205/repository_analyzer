package com.ivan_naumenko.repository_analyzer.report.exception;

public class ReportNotReadyException extends RuntimeException {

    public ReportNotReadyException(String message) {
        super(message);
    }
}
