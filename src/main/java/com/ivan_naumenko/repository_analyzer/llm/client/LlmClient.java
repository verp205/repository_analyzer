package com.ivan_naumenko.repository_analyzer.llm.client;

import com.ivan_naumenko.repository_analyzer.llm.client.config.LlmProperties;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmCancelledException;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmRequestException;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestOptions;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.Semaphore;

@Service
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final LlmTransport transport;
    private final LlmProperties properties;
    private final Semaphore permits;

    public LlmClient(
            LlmTransport transport,
            LlmProperties properties
    ) {
        this.transport = transport;
        this.properties = properties;
        this.permits = new Semaphore(
                properties.maxConcurrentRequests(),
                true
        );
    }

    public String generate(String prompt) {
        return generate(prompt, LlmRequestProfile.COMMIT_ANALYSIS);
    }

    public String generate(String prompt, LlmRequestProfile profile) {
        Objects.requireNonNull(prompt, "prompt не должен быть null");
        Objects.requireNonNull(profile, "profile не должен быть null");
        LlmRequestOptions options = properties.optionsFor(profile);
        int attempts = Math.max(1, properties.maxAttempts());

        for (int attempt = 1; ; attempt++) {
            try {
                return generateOnce(prompt, options);
            } catch (LlmRequestException exception) {
                if (exception instanceof LlmCancelledException
                        || attempt >= attempts
                        || Thread.currentThread().isInterrupted()) {
                    throw exception;
                }
                log.warn(
                        "LLM-запрос не выполнен, попытка {} из {}: {}",
                        attempt,
                        attempts,
                        exception.getMessage()
                );
                pause(properties.retryBackoffMillis() * attempt);
            }
        }
    }

    public String cacheNamespace() {
        return transport.cacheNamespace();
    }

    public String cacheNamespace(LlmRequestProfile profile) {
        Objects.requireNonNull(profile, "profile не должен быть null");
        return "%s:%s:%s".formatted(
                transport.cacheNamespace(),
                profile.name(),
                properties.optionsFor(profile).cacheKey()
        );
    }

    private String generateOnce(String prompt, LlmRequestOptions options) {
        boolean acquired = false;
        try {
            permits.acquire();
            acquired = true;
            return transport.generate(prompt, options);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmCancelledException(
                    "Ожидание доступа к inference-серверу прервано",
                    exception
            );
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }

    private void pause(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmCancelledException(
                    "Повтор LLM-запроса прерван",
                    exception
            );
        }
    }
}
