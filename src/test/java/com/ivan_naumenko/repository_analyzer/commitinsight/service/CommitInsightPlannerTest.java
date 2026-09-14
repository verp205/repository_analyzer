package com.ivan_naumenko.repository_analyzer.commitinsight.service;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.ChangeCategory;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDraft;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.InsightWorkUnit;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileRow;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;
import com.ivan_naumenko.repository_analyzer.report.config.ReportProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitInsightPlannerTest {

    private final CommitInsightPlanner planner = new CommitInsightPlanner(
            new ReportProperties(2, 4_000, 2, 2, 40)
    );

    @Test
    void separatesTemplatesBatchesAndLargeCommits() {
        CommitChangeSummary merge = commit(1, 10, List.of("src/A.java"), 2);
        CommitChangeSummary empty = commit(2, 0, List.of(), 1);
        CommitChangeSummary lockOnly = commit(3, 500, List.of("frontend/package-lock.json"), 1);
        CommitChangeSummary smallFirst = commit(4, 5, List.of("README.md"), 1);
        CommitChangeSummary large = commit(5, 400, List.of("src/Big.java"), 1);
        CommitChangeSummary smallSecond = commit(6, 12, List.of("src/Small.java"), 1);
        CommitChangeSummary smallThird = commit(7, 3, List.of("src/Tiny.java"), 1);

        List<InsightWorkUnit> units = planner.plan(List.of(
                merge, empty, lockOnly, smallFirst, large, smallSecond, smallThird
        ));

        assertEquals(4, units.size());
        assertEquals(InsightWorkUnit.Kind.TEMPLATE, units.get(0).kind());
        assertEquals(List.of(merge, empty, lockOnly), units.get(0).commits());
        assertEquals(InsightWorkUnit.Kind.SINGLE, units.get(1).kind());
        assertEquals(List.of(large), units.get(1).commits());
        assertEquals(InsightWorkUnit.Kind.BATCH, units.get(2).kind());
        assertEquals(List.of(smallFirst, smallSecond), units.get(2).commits());
        assertEquals(InsightWorkUnit.Kind.SINGLE, units.get(3).kind());
        assertEquals(List.of(smallThird), units.get(3).commits());
    }

    @Test
    void describesTemplateCommitsWithoutLlm() {
        CommitInsightDraft merge = CommitInsightTemplates.templateFor(
                commit(1, 10, List.of("src/A.java"), 2)
        ).orElseThrow();
        CommitInsightDraft lock = CommitInsightTemplates.templateFor(
                commit(2, 10, List.of("yarn.lock"), 1)
        ).orElseThrow();

        assertEquals(ChangeCategory.MERGE, merge.category());
        assertTrue(merge.summary().startsWith("Слияние веток: Commit 1"));
        assertEquals(ChangeCategory.BUILD, lock.category());
        assertTrue(CommitInsightTemplates.templateFor(
                commit(3, 10, List.of("src/A.java"), 1)
        ).isEmpty());
    }

    private CommitChangeSummary commit(
            int id,
            int lines,
            List<String> paths,
            int parents
    ) {
        return new CommitChangeSummary(
                id,
                id,
                "%040d".formatted(id),
                "Commit " + id,
                "Author",
                Instant.parse("2026-07-20T10:00:00Z"),
                lines,
                0,
                paths.stream()
                        .map(path -> new ChangedFileRow(
                                id,
                                path,
                                path,
                                FileChangeType.MODIFY,
                                lines,
                                0
                        ))
                        .toList(),
                parents
        );
    }
}
