package com.ivan_naumenko.repository_analyzer.git.model;

import java.time.Instant;
import java.util.List;

public record GitCommit(
        String commitHash,
        List<String> parentHashes,
        String authorName,
        String authorMail,
        Instant committedAt,
        String message
) {
}
