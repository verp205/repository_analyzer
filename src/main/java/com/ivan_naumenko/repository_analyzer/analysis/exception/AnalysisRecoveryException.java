package com.ivan_naumenko.repository_analyzer.analysis.exception;

public class AnalysisRecoveryException extends RuntimeException {

    public AnalysisRecoveryException(Integer taskId, Throwable cause) {
        super("Не удалось восстановить задачу анализа: " + taskId, cause);
    }
}
