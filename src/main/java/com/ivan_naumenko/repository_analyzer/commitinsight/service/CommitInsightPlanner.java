package com.ivan_naumenko.repository_analyzer.commitinsight.service;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.InsightWorkUnit;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.report.config.ReportProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Разбивает коммиты на единицы работы: шаблонные описания без LLM,
 * пакеты небольших коммитов и отдельные запросы для крупных изменений.
 */
@Service
@RequiredArgsConstructor
public class CommitInsightPlanner {

    private static final int SMALL_COMMIT_MAX_FILES = 4;

    private final ReportProperties properties;

    public List<InsightWorkUnit> plan(List<CommitChangeSummary> commits) {
        List<InsightWorkUnit> units = new ArrayList<>();
        List<CommitChangeSummary> templates = new ArrayList<>();
        List<CommitChangeSummary> smallCommits = new ArrayList<>();

        for (CommitChangeSummary commit : commits) {
            if (CommitInsightTemplates.templateFor(commit).isPresent()) {
                templates.add(commit);
            } else if (properties.commitBatchSize() > 1 && isSmall(commit)) {
                smallCommits.add(commit);
                if (smallCommits.size() == properties.commitBatchSize()) {
                    units.add(batch(smallCommits));
                    smallCommits = new ArrayList<>();
                }
            } else {
                units.add(InsightWorkUnit.single(commit));
            }
        }

        if (!smallCommits.isEmpty()) {
            units.add(batch(smallCommits));
        }
        if (!templates.isEmpty()) {
            units.addFirst(new InsightWorkUnit(
                    InsightWorkUnit.Kind.TEMPLATE,
                    templates
            ));
        }
        return units;
    }

    private boolean isSmall(CommitChangeSummary commit) {
        return commit.totalLines() <= properties.smallCommitMaxLines()
                && commit.files().size() <= SMALL_COMMIT_MAX_FILES;
    }

    private InsightWorkUnit batch(List<CommitChangeSummary> commits) {
        return commits.size() == 1
                ? InsightWorkUnit.single(commits.getFirst())
                : new InsightWorkUnit(InsightWorkUnit.Kind.BATCH, commits);
    }
}
