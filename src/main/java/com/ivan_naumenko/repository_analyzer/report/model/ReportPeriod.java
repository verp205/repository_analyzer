package com.ivan_naumenko.repository_analyzer.report.model;

import java.util.regex.Pattern;

public record ReportPeriod(
        ReportPeriodType type,
        Integer days,
        String fromTag,
        String toTag
) {

    private static final Pattern TAG_NAME = Pattern.compile("^[\\p{L}\\p{N}._/+@-]{1,200}$");
    private static final int MAX_DAYS = 3_650;

    public ReportPeriod {
        type = type == null ? ReportPeriodType.ALL : type;
        switch (type) {
            case ALL -> {
                days = null;
                fromTag = null;
                toTag = null;
            }
            case LAST_DAYS -> {
                if (days == null || days < 1 || days > MAX_DAYS) {
                    throw new IllegalArgumentException(
                            "Период в днях должен быть от 1 до " + MAX_DAYS
                    );
                }
                fromTag = null;
                toTag = null;
            }
            case SINCE_TAG -> {
                fromTag = requireTag(fromTag, "Укажите тег, после которого строить отчёт");
                days = null;
                toTag = null;
            }
            case BETWEEN_TAGS -> {
                fromTag = requireTag(fromTag, "Укажите начальный тег");
                toTag = requireTag(toTag, "Укажите конечный тег");
                if (fromTag.equals(toTag)) {
                    throw new IllegalArgumentException("Начальный и конечный теги должны различаться");
                }
                days = null;
            }
        }
    }

    public static ReportPeriod all() {
        return new ReportPeriod(ReportPeriodType.ALL, null, null, null);
    }

    /** Ключ уникальности отчёта внутри задачи и роли. */
    public String key() {
        return switch (type) {
            case ALL -> "ALL";
            case LAST_DAYS -> "LAST_DAYS:" + days;
            case SINCE_TAG -> "SINCE_TAG:" + fromTag;
            case BETWEEN_TAGS -> "BETWEEN_TAGS:" + fromTag + ".." + toTag;
        };
    }

    public String label() {
        return switch (type) {
            case ALL -> "вся история";
            case LAST_DAYS -> switch (days) {
                case 30 -> "последний месяц истории";
                case 90 -> "последние 3 месяца истории";
                case 180 -> "последние 6 месяцев истории";
                case 365 -> "последний год истории";
                default -> "последние " + days + " дн. истории";
            };
            case SINCE_TAG -> "изменения после тега " + fromTag;
            case BETWEEN_TAGS -> "изменения от тега " + fromTag + " до тега " + toTag;
        };
    }

    private static String requireTag(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String tag = value.strip();
        if (!TAG_NAME.matcher(tag).matches()) {
            throw new IllegalArgumentException("Некорректное имя тега: " + tag);
        }
        return tag;
    }
}
