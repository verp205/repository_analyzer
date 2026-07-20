package com.ivan_naumenko.repository_analyzer.git.exception;

public class RepositoryReadException extends RuntimeException {

    public RepositoryReadException(String message) {
        super(message);
    }

    public RepositoryReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
