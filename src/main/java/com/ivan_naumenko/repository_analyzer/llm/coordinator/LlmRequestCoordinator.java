package com.ivan_naumenko.repository_analyzer.llm.coordinator;

import com.ivan_naumenko.repository_analyzer.llm.cache.service.LlmCacheService;
import com.ivan_naumenko.repository_analyzer.llm.client.LlmClient;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmCancelledException;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmRequestException;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import com.ivan_naumenko.repository_analyzer.llm.prompt.service.PromptHashService;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
        return getOrGenerate(
                prompt,
                audience,
                LlmRequestProfile.COMMIT_ANALYSIS
        );
    }

    public String getOrGenerate(
            String prompt,
            ReportAudience audience,
            LlmRequestProfile profile
    ) {
        // audience == null означает общий для всех ролей запрос.
        Objects.requireNonNull(profile, "profile не должен быть null");
        String cacheNamespace = Objects.requireNonNull(
                llmClient.cacheNamespace(profile),
                "LLM cacheNamespace не должен быть null"
        );
        String promptHash = promptHashService.calculate(
                cacheNamespace + "\n" + prompt
        );

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
            try {
                return await(existingFuture);
            } catch (LlmCancelledException cancelled) {
                if (Thread.currentThread().isInterrupted()) {
                    throw cancelled;
                }
                // Такой же запрос другого отчёта остановили — выполняем его сами.
                return getOrGenerate(prompt, audience, profile);
            }
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

            String generated = llmClient.generate(prompt, profile);
            String stored = writeCoordinator.execute(
                    () -> cacheService.saveResponse(
                            promptHash,
                            cacheNamespace,
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
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmCancelledException(
                    "Ожидание LLM-ответа прервано",
                    exception
            );
        } catch (ExecutionException exception) {
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
