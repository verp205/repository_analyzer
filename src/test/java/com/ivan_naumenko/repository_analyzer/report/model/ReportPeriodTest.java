package com.ivan_naumenko.repository_analyzer.report.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportPeriodTest {

    @Test
    void buildsStableKeysAndReadableLabels() {
        assertEquals("ALL", ReportPeriod.all().key());
        assertEquals("вся история", ReportPeriod.all().label());

        ReportPeriod quarter = new ReportPeriod(ReportPeriodType.LAST_DAYS, 90, "ignored", "ignored");
        assertEquals("LAST_DAYS:90", quarter.key());
        assertEquals("последние 3 месяца истории", quarter.label());
        assertNull(quarter.fromTag());

        ReportPeriod sinceTag = new ReportPeriod(ReportPeriodType.SINCE_TAG, 5, " v1.2 ", "v2");
        assertEquals("SINCE_TAG:v1.2", sinceTag.key());
        assertEquals("изменения после тега v1.2", sinceTag.label());
        assertNull(sinceTag.days());
        assertNull(sinceTag.toTag());

        ReportPeriod release = new ReportPeriod(ReportPeriodType.BETWEEN_TAGS, null, "v1.1", "release/1.2");
        assertEquals("BETWEEN_TAGS:v1.1..release/1.2", release.key());
        assertEquals("изменения от тега v1.1 до тега release/1.2", release.label());

        assertEquals("ALL", new ReportPeriod(null, 10, "a", "b").key());
    }

    @Test
    void rejectsIncompleteOrUnsafePeriods() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReportPeriod(ReportPeriodType.LAST_DAYS, 0, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ReportPeriod(ReportPeriodType.LAST_DAYS, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ReportPeriod(ReportPeriodType.SINCE_TAG, null, " ", null));
        assertThrows(IllegalArgumentException.class,
                () -> new ReportPeriod(ReportPeriodType.BETWEEN_TAGS, null, "v1", null));
        assertThrows(IllegalArgumentException.class,
                () -> new ReportPeriod(ReportPeriodType.BETWEEN_TAGS, null, "v1", "v1"));
        assertThrows(IllegalArgumentException.class,
                () -> new ReportPeriod(ReportPeriodType.SINCE_TAG, null, "v1 --all", null));
    }
}
