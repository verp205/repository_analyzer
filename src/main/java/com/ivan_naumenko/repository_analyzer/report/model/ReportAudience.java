package com.ivan_naumenko.repository_analyzer.report.model;

public enum ReportAudience {
    DEVELOPER,
    ANALYST,
    SECURITY,
    TEAM_LEAD,
    DIRECTOR;

    public String displayName() {
        return switch (this) {
            case DEVELOPER -> "Разработчик";
            case ANALYST -> "Аналитик";
            case SECURITY -> "Специалист по безопасности";
            case TEAM_LEAD -> "Тимлид";
            case DIRECTOR -> "Директор";
        };
    }
}
