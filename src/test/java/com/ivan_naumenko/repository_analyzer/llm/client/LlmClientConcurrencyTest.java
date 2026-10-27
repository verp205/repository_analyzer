package com.ivan_naumenko.repository_analyzer.llm.client;

import com.ivan_naumenko.repository_analyzer.llm.client.config.LlmProperties;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmCancelledException;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmRequestException;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestOptions;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmClientConcurrencyTest {

    @Test
    void limitsConcurrentTransportCalls() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        LlmTransport transport = new LlmTransport() {
            @Override
            public String cacheNamespace() {
                return "test-provider:test-model";
            }

            @Override
            public String generate(String prompt) {
                int current = active.incrementAndGet();
                maximumActive.accumulateAndGet(current, Math::max);
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test timeout");
                    }
                    return "response";
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                } finally {
                    active.decrementAndGet();
                }
            }
        };
        LlmClient client = new LlmClient(transport, properties(2, 1));

        try (ExecutorService executor = Executors.newFixedThreadPool(6)) {
            List<Future<String>> futures = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                futures.add(executor.submit(() -> client.generate("prompt")));
            }

            await(() -> maximumActive.get() == 2);
            release.countDown();

            for (Future<String> future : futures) {
                assertEquals("response", future.get(5, TimeUnit.SECONDS));
            }
        }

        assertEquals(2, maximumActive.get());
    }

    @Test
    void appliesProfileLimitsTimeoutsAndSeparatesCache() {
        AtomicReference<LlmRequestOptions> captured = new AtomicReference<>();
        LlmClient client = new LlmClient(capturingTransport(captured), properties(2, 1));

        assertEquals(
                "response",
                client.generate("prompt", LlmRequestProfile.SYNTHESIS_FINAL)
        );
        assertEquals(new LlmRequestOptions(1_800, "low", true, 600_000), captured.get());

        client.generate("prompt", LlmRequestProfile.COMMIT_ANALYSIS);
        assertEquals(new LlmRequestOptions(700, "none", true, 180_000), captured.get());

        client.generate("prompt", LlmRequestProfile.COMMIT_BATCH);
        assertEquals(new LlmRequestOptions(2_400, "none", true, 360_000), captured.get());

        assertTrue(client.cacheNamespace(LlmRequestProfile.COMMIT_ANALYSIS)
                .endsWith("maxTokens=700;reasoning=none;json"));
        assertTrue(client.cacheNamespace(LlmRequestProfile.SYNTHESIS_FINAL)
                .endsWith("maxTokens=1800;reasoning=low;json"));
    }

    @Test
    void retriesTransientFailuresBeforeGivingUp() {
        AtomicInteger calls = new AtomicInteger();
        LlmTransport flaky = new LlmTransport() {
            @Override
            public String cacheNamespace() {
                return "test-provider:test-model";
            }

            @Override
            public String generate(String prompt) {
                if (calls.incrementAndGet() < 3) {
                    throw new LlmRequestException("temporary failure");
                }
                return "response";
            }
        };

        assertEquals("response", new LlmClient(flaky, properties(1, 3)).generate("prompt"));
        assertEquals(3, calls.get());

        calls.set(-10);
        assertThrows(
                LlmRequestException.class,
                () -> new LlmClient(flaky, properties(1, 2)).generate("prompt")
        );
        assertEquals(-8, calls.get());
    }

    @Test
    void doesNotRetryCancelledRequests() {
        AtomicInteger calls = new AtomicInteger();
        LlmTransport cancelled = new LlmTransport() {
            @Override
            public String cacheNamespace() {
                return "test-provider:test-model";
            }

            @Override
            public String generate(String prompt) {
                calls.incrementAndGet();
                throw new LlmCancelledException("stopped");
            }
        };

        assertThrows(
                LlmCancelledException.class,
                () -> new LlmClient(cancelled, properties(1, 3)).generate("prompt")
        );
        assertEquals(1, calls.get());
    }

    private LlmTransport capturingTransport(AtomicReference<LlmRequestOptions> captured) {
        return new LlmTransport() {
            @Override
            public String cacheNamespace() {
                return "test-provider:test-model";
            }

            @Override
            public String generate(String prompt) {
                return "fallback";
            }

            @Override
            public String generate(String prompt, LlmRequestOptions options) {
                captured.set(options);
                return "response";
            }
        };
    }

    private LlmProperties properties(int maxConcurrentRequests, int maxAttempts) {
        return new LlmProperties(
                "stub",
                "test-provider",
                "http://localhost:1234",
                "/v1/chat/completions",
                "test-model",
                "",
                maxConcurrentRequests,
                700,
                "none",
                600,
                "none",
                1_800,
                "low",
                2_400,
                true,
                maxAttempts,
                0,
                10_000,
                180_000,
                600_000
        );
    }

    private void await(Check check) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!check.complete() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(check.complete(), "Условие не выполнилось вовремя");
    }

    @FunctionalInterface
    private interface Check {
        boolean complete();
    }
}
