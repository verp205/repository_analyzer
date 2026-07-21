package com.ivan_naumenko.repository_analyzer.llm.coordinator;

import com.ivan_naumenko.repository_analyzer.llm.cache.service.LlmCacheService;
import com.ivan_naumenko.repository_analyzer.llm.client.LlmClient;
import com.ivan_naumenko.repository_analyzer.llm.prompt.service.PromptHashService;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmRequestCoordinatorTest {

    private static final String PROMPT = "Analyze commit";
    private static final String CACHE_NAMESPACE = "test-provider:test-model";
    private static final String HASH_INPUT = CACHE_NAMESPACE + "\n" + PROMPT;
    private static final String HASH = "a".repeat(64);
    private static final String RESPONSE = "Analysis result";

    @Mock
    private LlmCacheService cacheService;

    @Mock
    private PromptHashService hashService;

    @Mock
    private LlmClient llmClient;

    @Test
    void returnsCachedResponseWithoutCallingLlm() {
        when(hashService.calculate(HASH_INPUT)).thenReturn(HASH);
        when(cacheService.findResponse(HASH))
                .thenReturn(Optional.of(RESPONSE));

        String result = coordinator().getOrGenerate(
                PROMPT,
                ReportAudience.DEVELOPER
        );

        assertEquals(RESPONSE, result);
        verify(llmClient, never()).generate(anyString());
    }

    @Test
    void sendsOneLlmRequestForTenConcurrentCallers() throws Exception {
        when(hashService.calculate(HASH_INPUT)).thenReturn(HASH);
        AtomicReference<String> cachedResponse = new AtomicReference<>();
        when(cacheService.findResponse(HASH)).thenAnswer(
                ignored -> Optional.ofNullable(cachedResponse.get())
        );
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch enteredLlm = new CountDownLatch(1);
        CountDownLatch releaseLlm = new CountDownLatch(1);
        when(llmClient.generate(PROMPT)).thenAnswer(ignored -> {
            calls.incrementAndGet();
            enteredLlm.countDown();
            if (!releaseLlm.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test timeout");
            }
            return RESPONSE;
        });
        when(cacheService.saveResponse(
                HASH,
                CACHE_NAMESPACE,
                PROMPT,
                RESPONSE,
                ReportAudience.SECURITY
        )).thenAnswer(ignored -> {
            cachedResponse.set(RESPONSE);
            return RESPONSE;
        });
        LlmRequestCoordinator coordinator = coordinator();

        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (int index = 0; index < 10; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return coordinator.getOrGenerate(
                            PROMPT,
                            ReportAudience.SECURITY
                    );
                }));
            }

            start.countDown();
            enteredLlm.await(5, TimeUnit.SECONDS);
            releaseLlm.countDown();

            for (Future<String> future : futures) {
                assertEquals(RESPONSE, future.get(5, TimeUnit.SECONDS));
            }
        }

        assertEquals(1, calls.get());
        verify(llmClient).generate(PROMPT);
        verify(cacheService).saveResponse(
                HASH,
                CACHE_NAMESPACE,
                PROMPT,
                RESPONSE,
                ReportAudience.SECURITY
        );
    }

    @Test
    void removesFailedRequestSoItCanBeRetried() {
        when(hashService.calculate(HASH_INPUT)).thenReturn(HASH);
        when(cacheService.findResponse(HASH)).thenReturn(Optional.empty());
        when(llmClient.generate(PROMPT))
                .thenThrow(new IllegalStateException("first failure"))
                .thenReturn(RESPONSE);
        when(cacheService.saveResponse(
                HASH,
                CACHE_NAMESPACE,
                PROMPT,
                RESPONSE,
                ReportAudience.ANALYST
        )).thenReturn(RESPONSE);
        LlmRequestCoordinator coordinator = coordinator();

        assertThrows(
                IllegalStateException.class,
                () -> coordinator.getOrGenerate(
                        PROMPT,
                        ReportAudience.ANALYST
                )
        );

        assertEquals(
                RESPONSE,
                coordinator.getOrGenerate(PROMPT, ReportAudience.ANALYST)
        );
    }

    private LlmRequestCoordinator coordinator() {
        when(llmClient.cacheNamespace()).thenReturn(CACHE_NAMESPACE);
        return new LlmRequestCoordinator(
                cacheService,
                hashService,
                llmClient,
                new SqliteWriteCoordinator()
        );
    }
}
