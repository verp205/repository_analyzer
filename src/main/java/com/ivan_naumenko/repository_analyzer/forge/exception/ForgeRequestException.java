package com.ivan_naumenko.repository_analyzer.forge.exception;

public class ForgeRequestException extends RuntimeException {

    public ForgeRequestException(String message) {
        super(message);
    }

    public ForgeRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
