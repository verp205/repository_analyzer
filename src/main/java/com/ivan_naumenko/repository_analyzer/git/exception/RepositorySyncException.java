package com.ivan_naumenko.repository_analyzer.git.exception;

public class RepositorySyncException extends RuntimeException {
    public RepositorySyncException(String s, Exception exception) {
        super(s, exception);
    }
}
