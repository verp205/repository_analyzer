package com.ivan_naumenko.repository_analyzer.llm.client;

import com.ivan_naumenko.repository_analyzer.llm.client.config.LlmProperties;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmRequestException;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestOptions;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.Semaphore;

@Service
public class LlmClient {

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
        boolean acquired = false;

        try {
            permits.acquire();
            acquired = true;
            return transport.generate(prompt, options);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmRequestException(
                    "Ожидание доступа к inference-серверу прервано",
                    exception
            );
        } finally {
            if (acquired) {
                permits.release();
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
}
