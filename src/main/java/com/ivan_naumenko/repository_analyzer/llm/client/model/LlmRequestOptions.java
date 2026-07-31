package com.ivan_naumenko.repository_analyzer.llm.client.model;

import java.util.Locale;
import java.util.Set;

public record LlmRequestOptions(
        int maxTokens,
        String reasoningEffort
) {

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
        reasoningEffort = normalize(reasoningEffort);
        if (reasoningEffort != null
                && !ALLOWED_REASONING_EFFORTS.contains(reasoningEffort)) {
            throw new IllegalArgumentException(
                    "Неподдерживаемый reasoningEffort: " + reasoningEffort
            );
        }
    }

    public String cacheKey() {
        return "maxTokens=%d;reasoning=%s".formatted(
                maxTokens,
                reasoningEffort == null ? "default" : reasoningEffort
        );
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
