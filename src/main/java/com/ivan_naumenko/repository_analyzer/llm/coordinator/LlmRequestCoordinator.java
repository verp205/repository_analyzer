package com.ivan_naumenko.repository_analyzer.llm.coordinator;

import com.ivan_naumenko.repository_analyzer.llm.cache.service.LlmCacheService;
import com.ivan_naumenko.repository_analyzer.llm.client.LlmClient;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmRequestException;
import com.ivan_naumenko.repository_analyzer.llm.prompt.service.PromptHashService;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class LlmRequestCoordinator {

    private final LlmCacheService cacheService;
    private final PromptHashService promptHashService;
    private final LlmClient llmClient;
    private final SqliteWriteCoordinator writeCoordinator;
    private final ConcurrentHashMap<String, CompletableFuture<String>> inFlight =
            new ConcurrentHashMap<>();

    public String getOrGenerate(
            String prompt,
            ReportAudience audience
    ) {
        Objects.requireNonNull(audience, "audience не должен быть null");
        String promptHash = promptHashService.calculate(prompt);

        Optional<String> cached = cacheService.findResponse(promptHash);
        if (cached.isPresent()) {
            return cached.get();
        }

        CompletableFuture<String> ownFuture = new CompletableFuture<>();
        CompletableFuture<String> existingFuture = inFlight.putIfAbsent(
                promptHash,
                ownFuture
        );

        if (existingFuture != null) {
            return await(existingFuture);
        }

        try {
            Optional<String> cachedAfterClaim = cacheService.findResponse(
                    promptHash
            );
            if (cachedAfterClaim.isPresent()) {
                String response = cachedAfterClaim.get();
                ownFuture.complete(response);
                return response;
            }

            String generated = llmClient.generate(prompt);
            String stored = writeCoordinator.execute(
                    () -> cacheService.saveResponse(
                            promptHash,
                            prompt,
                            generated,
                            audience
                    )
            );
            ownFuture.complete(stored);
            return stored;
        } catch (RuntimeException exception) {
            ownFuture.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(promptHash, ownFuture);
        }
    }

    private String await(CompletableFuture<String> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new LlmRequestException(
                    "Ожидаемый LLM-запрос завершился ошибкой",
                    cause
            );
        }
    }
}
