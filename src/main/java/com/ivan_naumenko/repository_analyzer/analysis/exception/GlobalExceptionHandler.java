package com.ivan_naumenko.repository_analyzer.analysis.exception;

import com.ivan_naumenko.repository_analyzer.analysis.exception.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.concurrent.RejectedExecutionException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AnalysisTaskNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            AnalysisTaskNotFoundException ex
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        ex.getMessage(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(RejectedExecutionException.class)
    public ResponseEntity<ApiErrorResponse> handleRejectedExecution(
            RejectedExecutionException ex
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        ex.getMessage(),
                        LocalDateTime.now()
                ));
    }
}
