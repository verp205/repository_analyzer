package com.ivan_naumenko.repository_analyzer.llm.client;

import com.ivan_naumenko.repository_analyzer.llm.client.config.LlmProperties;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmCancelledException;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmRequestException;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestOptions;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleLlmTransportTest {

    private static final String RESPONSE = """
            {"choices":[{"message":{"role":"assistant","content":"Готово"}}]}
            """;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private volatile long responseDelayMillis;
    private volatile int responseStatus = 200;
    private volatile String responseBody = RESPONSE;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/v1/chat/completions", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try {
                if (responseDelayMillis > 0) {
                    Thread.sleep(responseDelayMillis);
                }
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Клиент закрыл соединение — это и проверяется в тестах отмены.
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    void sendsLimitsAndCredentialsAndReadsContent() {
        String result = transport().generate(
                "Короткий prompt",
                new LlmRequestOptions(700, "none", false, 5_000)
        );

        assertEquals("Готово", result);
        assertEquals("Bearer secret", lastAuthorization.get());
        assertTrue(lastBody.get().contains("\"max_tokens\":700"));
        assertTrue(lastBody.get().contains("\"reasoning_effort\":\"none\""));
        assertTrue(lastBody.get().contains("\"stream\":false"));
        assertFalse(lastBody.get().contains("response_format"));
    }

    @Test
    void requestsJsonObjectWhenJsonModeIsEnabled() {
        transport().generate("prompt", new LlmRequestOptions(700, "none", true, 5_000));

        assertTrue(lastBody.get().contains("\"response_format\":{\"type\":\"json_object\"}"));
    }

    @Test
    void failsWithTimeoutInsteadOfWaitingForever() {
        responseDelayMillis = 3_000;
        long started = System.nanoTime();

        LlmRequestException exception = assertThrows(
                LlmRequestException.class,
                () -> transport().generate("prompt", new LlmRequestOptions(700, "none", false, 300))
        );

        assertTrue(exception.getMessage().contains("не ответил"), exception.getMessage());
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2_000);
    }

    @Test
    void interruptionCancelsInFlightRequest() throws Exception {
        responseDelayMillis = 10_000;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                transport().generate("prompt", new LlmRequestOptions(700, "none", false, 60_000));
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });

        caller.start();
        Thread.sleep(300);
        caller.interrupt();
        caller.join(2_000);

        assertFalse(caller.isAlive(), "Запрос не был прерван");
        assertInstanceOf(LlmCancelledException.class, failure.get());
    }

    @Test
    void reportsHttpErrorsAndEmptyContent() {
        responseStatus = 500;
        responseBody = "{\"error\":\"model not loaded\"}";
        LlmRequestException httpError = assertThrows(
                LlmRequestException.class,
                () -> transport().generate("prompt", new LlmRequestOptions(700, "none"))
        );
        assertTrue(httpError.getMessage().contains("HTTP 500"));
        assertTrue(httpError.getMessage().contains("model not loaded"));

        responseStatus = 200;
        responseBody = "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}";
        LlmRequestException empty = assertThrows(
                LlmRequestException.class,
                () -> transport().generate("prompt", new LlmRequestOptions(700, "none"))
        );
        assertTrue(empty.getMessage().contains("пустой ответ"));
    }

    private OpenAiCompatibleLlmTransport transport() {
        return new OpenAiCompatibleLlmTransport(properties(), JsonMapper.builder().build());
    }

    private LlmProperties properties() {
        return new LlmProperties(
                "openai-compatible",
                "ollama",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                "/v1/chat/completions",
                "qwen3:8b",
                "secret",
                4,
                700,
                "none",
                600,
                "none",
                1_800,
                "low",
                2_400,
                true,
                3,
                0,
                2_000,
                180_000,
                600_000
        );
    }
}
