package com.ivan_naumenko.repository_analyzer.llm.client;

import com.ivan_naumenko.repository_analyzer.llm.client.config.LlmProperties;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmRequestException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.Semaphore;

@Service
public class LlmClient {

    private final LlmTransport transport;
    private final Semaphore permits;

    public LlmClient(
            LlmTransport transport,
            LlmProperties properties
    ) {
        this.transport = transport;
        this.permits = new Semaphore(
                properties.maxConcurrentRequests(),
                true
        );
    }

    public String generate(String prompt) {
        Objects.requireNonNull(prompt, "prompt не должен быть null");
        boolean acquired = false;

        try {
            permits.acquire();
            acquired = true;
            return transport.generate(prompt);
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
}
