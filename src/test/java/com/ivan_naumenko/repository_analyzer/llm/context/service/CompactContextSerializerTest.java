package com.ivan_naumenko.repository_analyzer.llm.context.service;

import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactCommitContext;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactFileContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactContextSerializerTest {

    private final CompactContextSerializer serializer =
            new CompactContextSerializer(new ObjectMapper());

    @Test
    void serializesContextAndNestedFiles() {
        CompactCommitContext context = context();

        String json = serializer.serialize(context);

        assertTrue(json.contains("\"commitHash\":\""
                + context.commitHash()
                + "\""));
        assertTrue(json.contains("\"message\":\"Test commit\""));
        assertTrue(json.contains("\"path\":\"README.md\""));
        assertTrue(json.contains("\"changeType\":\"MODIFY\""));
        assertTrue(json.contains("\"javaAnalysis\":null"));
    }

    @Test
    void producesSameJsonForSameContext() {
        CompactCommitContext context = context();

        assertEquals(
                serializer.serialize(context),
                serializer.serialize(context)
        );
    }

    @Test
    void rejectsNullContext() {
        assertThrows(
                NullPointerException.class,
                () -> serializer.serialize(null)
        );
    }

    private CompactCommitContext context() {
        CompactFileContext file = new CompactFileContext(
                "README.md",
                FileChangeType.MODIFY,
                2,
                1,
                "patch",
                false,
                null
        );
        return new CompactCommitContext(
                "a".repeat(40),
                "b".repeat(40),
                "Test commit",
                "Test User",
                Instant.parse("2026-07-20T10:00:00Z"),
                2,
                1,
                List.of(file),
                0
        );
    }
}
