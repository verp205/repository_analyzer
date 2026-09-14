package com.ivan_naumenko.repository_analyzer.report.model;

public enum ReportPeriodType {
    /** Вся история ветки. */
    ALL,
    /** Последние N дней, считая от самого нового коммита. */
    LAST_DAYS,
    /** Коммиты после тега (не входящие в историю тега). */
    SINCE_TAG,
    /** Коммиты между двумя тегами: входят во второй, но не в первый. */
    BETWEEN_TAGS
}
