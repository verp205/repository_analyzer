package com.ivan_naumenko.repository_analyzer.git.exception;

public class BranchNotFoundException extends RuntimeException {

    public BranchNotFoundException(String message) {
        super(message);
    }
}
