package com.ivan_naumenko.repository_analyzer.evaluation;

import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactCommitContext;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactFileContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/** Эталонный коммит с ожидаемой категорией и уровнем риска. */
public record GoldenCommitCase(
        String id,
        String description,
        Expected expected,
        Commit commit
) {

    /** Допустимые значения: первое — наиболее ожидаемое, остальные тоже засчитываются. */
    public record Expected(List<String> categories, List<String> riskLevels) {
    }

    public record Commit(String message, List<File> files) {
    }

    public record File(
            String path,
            String changeType,
            int addedLines,
            int deletedLines,
            String patch
    ) {
    }

    public CompactCommitContext toContext() {
        List<CompactFileContext> files = commit.files().stream()
                .map(file -> new CompactFileContext(
                        file.path(),
                        FileChangeType.valueOf(file.changeType()),
                        file.addedLines(),
                        file.deletedLines(),
                        file.patch(),
                        false,
                        null
                ))
                .toList();
        return new CompactCommitContext(
                hash(),
                null,
                commit.message(),
                "Golden Author",
                Instant.parse("2026-01-15T10:00:00Z"),
                commit.files().stream().mapToInt(File::addedLines).sum(),
                commit.files().stream().mapToInt(File::deletedLines).sum(),
                files,
                0
        );
    }

    /** Стабильный псевдо-хэш коммита, чтобы промпт не менялся между прогонами. */
    public String hash() {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-1").digest(id.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
