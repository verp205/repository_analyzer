package com.ivan_naumenko.repository_analyzer.commitinsight.service;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.ChangeCategory;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDetails;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDraft;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightStatus;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.RiskLevel;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileRow;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Описания коммитов, для которых запрос к LLM не несёт пользы.
 */
public final class CommitInsightTemplates {

    private static final int MAX_MESSAGE_LENGTH = 160;
    private static final int MAX_ERROR_LENGTH = 200;
    private static final Set<String> LOCK_FILES = Set.of(
            "package-lock.json",
            "npm-shrinkwrap.json",
            "yarn.lock",
            "pnpm-lock.yaml",
            "gradle.lockfile",
            "poetry.lock",
            "pipfile.lock",
            "cargo.lock",
            "gemfile.lock",
            "composer.lock",
            "go.sum"
    );

    private CommitInsightTemplates() {
    }

    public static Optional<CommitInsightDraft> templateFor(
            CommitChangeSummary commit
    ) {
        if (commit.merge()) {
            return Optional.of(new CommitInsightDraft(
                    CommitInsightStatus.TEMPLATE,
                    ChangeCategory.MERGE,
                    RiskLevel.NONE,
                    "Слияние веток: " + shortMessage(commit.message()),
                    CommitInsightDetails.ofKeyChanges(List.of(
                            "Относительно первой родительской ветки: файлов %d, строк +%d / −%d"
                                    .formatted(
                                            commit.files().size(),
                                            commit.addedLines(),
                                            commit.deletedLines()
                                    )
                    ))
            ));
        }
        if (commit.files().isEmpty()) {
            return Optional.of(new CommitInsightDraft(
                    CommitInsightStatus.TEMPLATE,
                    ChangeCategory.CHORE,
                    RiskLevel.NONE,
                    "Коммит не содержит изменений файлов.",
                    CommitInsightDetails.empty()
            ));
        }
        if (commit.files().stream().allMatch(CommitInsightTemplates::isLockFile)) {
            return Optional.of(new CommitInsightDraft(
                    CommitInsightStatus.TEMPLATE,
                    ChangeCategory.BUILD,
                    RiskLevel.LOW,
                    "Обновлены lock-файлы зависимостей.",
                    CommitInsightDetails.ofKeyChanges(commit.files().stream()
                            .map(ChangedFileRow::path)
                            .limit(4)
                            .toList())
            ));
        }
        return Optional.empty();
    }

    public static CommitInsightDraft unstructured(String summary) {
        return new CommitInsightDraft(
                CommitInsightStatus.UNSTRUCTURED,
                ChangeCategory.CHORE,
                RiskLevel.UNKNOWN,
                summary == null || summary.isBlank()
                        ? "Модель вернула пустое описание."
                        : summary,
                CommitInsightDetails.empty()
        );
    }

    public static CommitInsightDraft failed(Throwable exception) {
        String reason = Objects.requireNonNullElse(
                exception.getMessage(),
                exception.getClass().getSimpleName()
        );
        if (reason.length() > MAX_ERROR_LENGTH) {
            reason = reason.substring(0, MAX_ERROR_LENGTH);
        }
        return new CommitInsightDraft(
                CommitInsightStatus.FAILED,
                ChangeCategory.CHORE,
                RiskLevel.UNKNOWN,
                "Не удалось получить анализ: " + reason,
                CommitInsightDetails.empty()
        );
    }

    private static boolean isLockFile(ChangedFileRow file) {
        String path = Objects.requireNonNullElse(file.path(), "");
        String name = path.substring(path.lastIndexOf('/') + 1)
                .toLowerCase(Locale.ROOT);
        return LOCK_FILES.contains(name);
    }

    private static String shortMessage(String message) {
        if (message == null || message.isBlank()) {
            return "без сообщения";
        }
        String line = message.lines().findFirst().orElse("").strip();
        return line.length() > MAX_MESSAGE_LENGTH
                ? line.substring(0, MAX_MESSAGE_LENGTH) + "…"
                : line;
    }
}
