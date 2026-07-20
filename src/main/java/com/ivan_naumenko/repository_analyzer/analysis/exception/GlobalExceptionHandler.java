package com.ivan_naumenko.repository_analyzer.analysis.exception;

import com.ivan_naumenko.repository_analyzer.analysis.exception.dto.ApiErrorResponse;
import com.ivan_naumenko.repository_analyzer.report.exception.ReportNotFoundException;
import com.ivan_naumenko.repository_analyzer.report.exception.ReportNotReadyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.LocalDateTime;
import java.util.concurrent.RejectedExecutionException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex
    ) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": "
                        + error.getDefaultMessage())
                .orElse("Некорректные данные запроса");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(
            HttpMessageNotReadableException ex
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "Некорректный JSON или неизвестное значение enum"
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex
    ) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

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

    @ExceptionHandler(AnalysisRecoveryException.class)
    public ResponseEntity<ApiErrorResponse> handleAnalysisRecovery(
            AnalysisRecoveryException ex
    ) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        ex.getMessage(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleReportNotFound(
            ReportNotFoundException ex
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        ex.getMessage(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(ReportNotReadyException.class)
    public ResponseEntity<ApiErrorResponse> handleReportNotReady(
            ReportNotReadyException ex
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        ex.getMessage(),
                        LocalDateTime.now()
                ));
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String message
    ) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        status.value(),
                        message,
                        LocalDateTime.now()
                ));
    }
}
