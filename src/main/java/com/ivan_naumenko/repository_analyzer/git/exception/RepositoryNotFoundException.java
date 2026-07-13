package com.ivan_naumenko.repository_analyzer.git.exception;

public class RepositoryNotFoundException extends RuntimeException {

    public RepositoryNotFoundException(String message) {
        super(message);
    }

    public RepositoryNotFoundException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}