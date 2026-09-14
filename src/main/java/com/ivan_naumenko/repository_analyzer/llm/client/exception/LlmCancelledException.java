package com.ivan_naumenko.repository_analyzer.llm.client.exception;

/**
 * Запрос к LLM прерван: отчёт остановили или поток получил interrupt.
 * Такие ошибки не повторяются.
 */
public class LlmCancelledException extends LlmRequestException {

    public LlmCancelledException(String message) {
        super(message);
    }

    public LlmCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
