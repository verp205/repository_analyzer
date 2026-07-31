package com.ivan_naumenko.repository_analyzer.report.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportTextSanitizerTest {

    private final ReportTextSanitizer sanitizer = new ReportTextSanitizer();

    @Test
    void convertsMarkdownTableToPlainList() {
        String result = sanitizer.sanitize("""
                ## Изменения

                | Файл | Риск |
                |---|---|
                | App.java | Низкий |
                """);

        assertFalse(result.contains("|---|"));
        assertFalse(result.contains("| Файл |"));
        assertTrue(result.contains("- Файл; Риск"));
        assertTrue(result.contains("- App.java; Низкий"));
    }

    @Test
    void preservesPipesInsideCodeFence() {
        String result = sanitizer.sanitize("""
                ```java
                value = left | right;
                ```
                """);

        assertTrue(result.contains("left | right"));
    }
}
