package com.ivan_naumenko.repository_analyzer.llm.prompt.service;

import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactCommitContext;
import com.ivan_naumenko.repository_analyzer.llm.context.service.CompactContextSerializer;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptFactoryTest {

    private static final String CONTEXT_JSON =
            "{\"commitHash\":\"abc\"}";

    @Mock
    private CompactContextSerializer serializer;

    @Test
    void buildsVersionedPromptWithAudienceInstructionAndContext() {
        CompactCommitContext context = context();
        when(serializer.serialize(context)).thenReturn(CONTEXT_JSON);
        PromptFactory factory = new PromptFactory(serializer);

        String prompt = factory.build(context, ReportAudience.SECURITY);

        assertTrue(prompt.contains("PROMPT_VERSION: v2"));
        assertTrue(prompt.contains("RESPONSE_LANGUAGE:"));
        assertTrue(prompt.contains(
                "Сформируй весь ответ исключительно на русском языке"
        ));
        assertTrue(prompt.contains("AUDIENCE_INSTRUCTION:"));
        assertTrue(prompt.contains("безопасности"));
        assertTrue(prompt.contains("<TECHNICAL_CONTEXT>"));
        assertTrue(prompt.contains(CONTEXT_JSON));
        verify(serializer).serialize(context);
    }

    @Test
    void createsDifferentPromptsForDifferentAudiences() {
        CompactCommitContext context = context();
        when(serializer.serialize(context)).thenReturn(CONTEXT_JSON);
        PromptFactory factory = new PromptFactory(serializer);

        String developerPrompt = factory.build(
                context,
                ReportAudience.DEVELOPER
        );
        String directorPrompt = factory.build(
                context,
                ReportAudience.DIRECTOR
        );

        assertNotEquals(developerPrompt, directorPrompt);
    }

    @Test
    void createsSamePromptForSameInputs() {
        CompactCommitContext context = context();
        when(serializer.serialize(context)).thenReturn(CONTEXT_JSON);
        PromptFactory factory = new PromptFactory(serializer);

        String first = factory.build(context, ReportAudience.ANALYST);
        String second = factory.build(context, ReportAudience.ANALYST);

        assertEquals(first, second);
    }

    @Test
    void rejectsNullArgumentsBeforeSerialization() {
        CompactCommitContext context = context();
        PromptFactory factory = new PromptFactory(serializer);

        assertThrows(
                NullPointerException.class,
                () -> factory.build(null, ReportAudience.DEVELOPER)
        );
        assertThrows(
                NullPointerException.class,
                () -> factory.build(context, null)
        );
        verify(serializer, never()).serialize(context);
    }

    private CompactCommitContext context() {
        return new CompactCommitContext(
                "a".repeat(40),
                null,
                "Test commit",
                "Test User",
                Instant.parse("2026-07-20T10:00:00Z"),
                1,
                0,
                List.of(),
                0
        );
    }
}
