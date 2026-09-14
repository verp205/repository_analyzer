package com.ivan_naumenko.repository_analyzer.report.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportTextSanitizerTest {

    private final ReportTextSanitizer sanitizer = new ReportTextSanitizer();

    @Test
    void removesReasoningOuterFenceAndTopLevelHeading() {
        String result = sanitizer.sanitize("""
                <think>
                скрытые рассуждения
                </think>
                ```markdown
                # Отчёт по проекту
                ## Главное

                - Пункт
                ```
                """);

        assertEquals("## Главное\n\n- Пункт", result);
    }

    @Test
    void keepsCodeBlocksAndConvertsTables() {
        String result = sanitizer.sanitize("""
                ## Риски

                | Риск | Уровень |
                |---|---|
                | Утечка | высокий |

                ```java
                # не заголовок
                ```
                """);

        assertEquals("""
                ## Риски

                - Риск; Уровень
                - Утечка; высокий

                ```java
                # не заголовок
                ```""", result);
    }

    @Test
    void dropsEmptySectionsAndRepeatedItems() {
        String result = sanitizer.sanitize("""
                ## Главное

                - Пароль БД записан в application.yml.

                ## Выявленные проблемы

                ## Находки

                - Пароль БД записан в application.yml.
                - Нет аутентификации API.
                - Нет аутентификации API.
                """);

        assertEquals("""
                ## Главное

                - Пароль БД записан в application.yml.

                ## Находки

                - Пароль БД записан в application.yml.
                - Нет аутентификации API.""", result);
    }

    @Test
    void keepsAnswerThatConsistsOnlyOfHeadings() {
        assertEquals("## Главное", sanitizer.sanitize("## Главное"));
    }

    @Test
    void rejectsEmptyAnswers() {
        assertThrows(IllegalArgumentException.class, () -> sanitizer.sanitize("<think>x</think>"));
    }
}
