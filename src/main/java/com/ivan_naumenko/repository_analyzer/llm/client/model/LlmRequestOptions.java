package com.ivan_naumenko.repository_analyzer.llm.client.model;

import java.util.Locale;
import java.util.Set;

public record LlmRequestOptions(
        int maxTokens,
        String reasoningEffort,
        boolean jsonMode,
        long timeoutMillis
) {

    public static final long DEFAULT_TIMEOUT_MILLIS = 300_000;

    private static final Set<String> ALLOWED_REASONING_EFFORTS = Set.of(
            "none",
            "low",
            "medium",
            "high"
    );

    public LlmRequestOptions {
        if (maxTokens < 1) {
            throw new IllegalArgumentException(
                    "maxTokens должен быть положительным"
            );
        }
        if (timeoutMillis < 1) {
            throw new IllegalArgumentException(
                    "timeoutMillis должен быть положительным"
            );
        }
        reasoningEffort = normalize(reasoningEffort);
        if (reasoningEffort != null
                && !ALLOWED_REASONING_EFFORTS.contains(reasoningEffort)) {
            throw new IllegalArgumentException(
                    "Неподдерживаемый reasoningEffort: " + reasoningEffort
            );
        }
    }

    public LlmRequestOptions(int maxTokens, String reasoningEffort) {
        this(maxTokens, reasoningEffort, false, DEFAULT_TIMEOUT_MILLIS);
    }

    public LlmRequestOptions(int maxTokens, String reasoningEffort, boolean jsonMode) {
        this(maxTokens, reasoningEffort, jsonMode, DEFAULT_TIMEOUT_MILLIS);
    }

    /** Таймаут не влияет на содержание ответа, поэтому не входит в ключ кэша. */
    public String cacheKey() {
        return "maxTokens=%d;reasoning=%s%s".formatted(
                maxTokens,
                reasoningEffort == null ? "default" : reasoningEffort,
                jsonMode ? ";json" : ""
        );
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
