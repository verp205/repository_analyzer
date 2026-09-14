package com.ivan_naumenko.repository_analyzer.commitinsight.model;

import java.util.Locale;

public enum RiskLevel {
    HIGH("высокий", 3),
    MEDIUM("средний", 2),
    LOW("низкий", 1),
    NONE("нет", 0),
    UNKNOWN("не определён", -1);

    private final String displayName;
    private final int rank;

    RiskLevel(String displayName, int rank) {
        this.displayName = displayName;
        this.rank = rank;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isAtLeast(RiskLevel other) {
        return rank >= other.rank;
    }

    public static RiskLevel parse(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
