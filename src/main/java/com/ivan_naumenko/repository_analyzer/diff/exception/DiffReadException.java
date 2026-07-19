package com.ivan_naumenko.repository_analyzer.diff.exception;

public class DiffReadException extends RuntimeException {

    public DiffReadException(String message) {
        super(message);
    }

    public DiffReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
