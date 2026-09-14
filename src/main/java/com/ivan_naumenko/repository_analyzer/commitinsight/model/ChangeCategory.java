package com.ivan_naumenko.repository_analyzer.commitinsight.model;

import java.util.Locale;

public enum ChangeCategory {
    FEATURE("Новая функциональность"),
    FIX("Исправление ошибок"),
    REFACTOR("Рефакторинг"),
    PERFORMANCE("Производительность"),
    SECURITY("Безопасность"),
    DATA("Данные и хранение"),
    API("API и интеграции"),
    TESTS("Тесты"),
    BUILD("Сборка и зависимости"),
    DOCS("Документация"),
    CHORE("Сопровождение"),
    MERGE("Слияние веток");

    private final String displayName;

    ChangeCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static ChangeCategory parse(String value) {
        if (value == null || value.isBlank()) {
            return CHORE;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return CHORE;
        }
    }
}
