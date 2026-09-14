package com.ivan_naumenko.repository_analyzer.git.model;

import java.time.Instant;

public record GitTag(
        String name,
        String commitHash,
        Instant committedAt
) {
}
