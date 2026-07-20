package com.ivan_naumenko.repository_analyzer.insight.cluster.model;

public enum ChangeClusterType {
    SECURITY("Безопасность"),
    TESTS("Тесты"),
    DOCUMENTATION("Документация"),
    BUILD_AND_CONFIGURATION("Сборка и конфигурация"),
    DATA("Данные и хранение"),
    API("API"),
    CORE("Основная логика");

    private final String displayName;

    ChangeClusterType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
