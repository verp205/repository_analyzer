package com.ivan_naumenko.repository_analyzer.llm.cache.service;

import com.ivan_naumenko.repository_analyzer.llm.cache.entity.LlmCacheEntry;
import com.ivan_naumenko.repository_analyzer.llm.cache.repository.LlmCacheRepository;
import com.ivan_naumenko.repository_analyzer.llm.prompt.service.PromptHashService;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class LlmCacheServiceTest {

    private static final String PROMPT = "Analyze this commit";
    private static final String RESPONSE = "No critical risks found";

    @Mock
    private LlmCacheRepository repository;

    private final PromptHashService promptHashService =
            new PromptHashService();

    @Test
    void returnsEmptyWhenResponseIsNotCached() {
        String promptHash = promptHashService.calculate(PROMPT);
        when(repository.findById(promptHash)).thenReturn(Optional.empty());

        Optional<String> result = service().findResponse(promptHash);

        assertFalse(result.isPresent());
    }

    @Test
    void returnsCachedResponse() {
        String promptHash = promptHashService.calculate(PROMPT);
        LlmCacheEntry entry = entry(promptHash, PROMPT, RESPONSE);
        when(repository.findById(promptHash))
                .thenReturn(Optional.of(entry));

        assertEquals(
                Optional.of(RESPONSE),
                service().findResponse(promptHash)
        );
    }

    @Test
    void savesCompletedResponse() {
        String promptHash = promptHashService.calculate(PROMPT);
        when(repository.findById(promptHash)).thenReturn(Optional.empty());

        String result = service().saveResponse(
                promptHash,
                PROMPT,
                RESPONSE,
                ReportAudience.SECURITY
        );

        assertEquals(RESPONSE, result);
        ArgumentCaptor<LlmCacheEntry> captor = ArgumentCaptor.forClass(
                LlmCacheEntry.class
        );
        verify(repository).saveAndFlush(captor.capture());
        LlmCacheEntry saved = captor.getValue();
        assertEquals(promptHash, saved.getPromptHash());
        assertEquals(PROMPT, saved.getPromptText());
        assertEquals(RESPONSE, saved.getResponseText());
        assertEquals(ReportAudience.SECURITY, saved.getAudience());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void keepsFirstResponseWhenEntryAlreadyExists() {
        String promptHash = promptHashService.calculate(PROMPT);
        LlmCacheEntry existing = entry(promptHash, PROMPT, RESPONSE);
        when(repository.findById(promptHash))
                .thenReturn(Optional.of(existing));

        String result = service().saveResponse(
                promptHash,
                PROMPT,
                "Another response",
                ReportAudience.SECURITY
        );

        assertEquals(RESPONSE, result);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsHashThatDoesNotBelongToPrompt() {
        String anotherHash = promptHashService.calculate("another prompt");

        assertThrows(
                IllegalArgumentException.class,
                () -> service().saveResponse(
                        anotherHash,
                        PROMPT,
                        RESPONSE,
                        ReportAudience.DEVELOPER
                )
        );
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidArguments() {
        String promptHash = promptHashService.calculate(PROMPT);

        assertThrows(
                IllegalArgumentException.class,
                () -> service().findResponse("not-a-hash")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service().saveResponse(
                        promptHash,
                        " ",
                        RESPONSE,
                        ReportAudience.ANALYST
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service().saveResponse(
                        promptHash,
                        PROMPT,
                        " ",
                        ReportAudience.ANALYST
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> service().saveResponse(
                        promptHash,
                        PROMPT,
                        RESPONSE,
                        null
                )
        );
    }

    @Test
    void rejectsExistingEntryWithConflictingMetadata() {
        String promptHash = promptHashService.calculate(PROMPT);
        LlmCacheEntry differentPrompt = entry(
                promptHash,
                "Different prompt",
                RESPONSE
        );
        when(repository.findById(promptHash))
                .thenReturn(Optional.of(differentPrompt));

        assertThrows(
                IllegalStateException.class,
                () -> service().saveResponse(
                        promptHash,
                        PROMPT,
                        RESPONSE,
                        ReportAudience.SECURITY
                )
        );

        LlmCacheEntry differentAudience = entry(
                promptHash,
                PROMPT,
                RESPONSE
        );
        differentAudience.setAudience(ReportAudience.DIRECTOR);
        when(repository.findById(promptHash))
                .thenReturn(Optional.of(differentAudience));

        assertThrows(
                IllegalStateException.class,
                () -> service().saveResponse(
                        promptHash,
                        PROMPT,
                        RESPONSE,
                        ReportAudience.SECURITY
                )
        );
    }

    private LlmCacheService service() {
        return new LlmCacheService(repository, promptHashService);
    }

    private LlmCacheEntry entry(
            String promptHash,
            String prompt,
            String response
    ) {
        LlmCacheEntry entry = new LlmCacheEntry();
        entry.setPromptHash(promptHash);
        entry.setPromptText(prompt);
        entry.setResponseText(response);
        entry.setAudience(ReportAudience.SECURITY);
        return entry;
    }
}
