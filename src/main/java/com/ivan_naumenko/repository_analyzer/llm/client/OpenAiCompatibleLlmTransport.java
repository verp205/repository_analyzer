package com.ivan_naumenko.repository_analyzer.llm.client;

import com.ivan_naumenko.repository_analyzer.llm.client.config.LlmProperties;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmCancelledException;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmRequestException;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestOptions;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Клиент OpenAI-совместимого API на java.net.http. Каждый запрос ограничен
 * таймаутом своего профиля, а прерывание потока закрывает HTTP-соединение,
 * поэтому остановленный отчёт перестаёт занимать модель.
 */
@Component
@ConditionalOnProperty(
        prefix = "repository-analyzer.llm",
        name = "provider",
        havingValue = "openai-compatible"
)
public class OpenAiCompatibleLlmTransport implements LlmTransport {

    private static final int MAX_ERROR_BODY_LENGTH = 300;

    private final LlmProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final URI endpoint;

    @Autowired
    public OpenAiCompatibleLlmTransport(LlmProperties properties, ObjectMapper mapper) {
        this(
                properties,
                mapper,
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()))
                        .build()
        );
    }

    OpenAiCompatibleLlmTransport(
            LlmProperties properties,
            ObjectMapper mapper,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = httpClient;
        String path = properties.chatPath().startsWith("/")
                ? properties.chatPath()
                : "/" + properties.chatPath();
        this.endpoint = URI.create(properties.baseUrl().replaceAll("/+$", "") + path);
    }

    @Override
    public String cacheNamespace() {
        return "%s:%s".formatted(
                properties.providerId(),
                properties.model()
        );
    }

    @Override
    public String generate(String prompt) {
        return generate(
                prompt,
                properties.optionsFor(LlmRequestProfile.COMMIT_ANALYSIS)
        );
    }

    @Override
    public String generate(String prompt, LlmRequestOptions options) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("temperature", 0.0);
        body.put("stream", false);
        body.put("max_tokens", options.maxTokens());
        if (options.reasoningEffort() != null) {
            body.put("reasoning_effort", options.reasoningEffort());
        }
        if (options.jsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMillis(options.timeoutMillis()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(body),
                        StandardCharsets.UTF_8
                ));
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            request.header("Authorization", "Bearer " + properties.apiKey());
        }

        CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(
                request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        HttpResponse<String> response;
        try {
            response = future.get(options.timeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new LlmCancelledException("LLM-запрос отменён", exception);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw timeout(options, exception);
        } catch (ExecutionException exception) {
            throw failure(options, exception.getCause());
        }

        if (response.statusCode() / 100 != 2) {
            throw new LlmRequestException(
                    "Inference-сервер вернул HTTP %d: %s".formatted(
                            response.statusCode(),
                            abbreviate(response.body())
                    )
            );
        }
        return extractContent(response.body());
    }

    private String extractContent(String responseBody) {
        JsonNode root;
        try {
            root = mapper.readTree(responseBody);
        } catch (RuntimeException exception) {
            throw new LlmRequestException(
                    "Inference-сервер вернул некорректный JSON",
                    exception
            );
        }
        JsonNode content = root == null
                ? null
                : root.path("choices").path(0).path("message").path("content");
        if (content == null || !content.isString() || content.stringValue().isBlank()) {
            throw new LlmRequestException("Inference-сервер вернул пустой ответ");
        }
        return content.stringValue();
    }

    private LlmRequestException failure(LlmRequestOptions options, Throwable cause) {
        if (cause instanceof HttpConnectTimeoutException
                || cause instanceof ConnectException
                || cause.getCause() instanceof ConnectException) {
            return new LlmRequestException(
                    "Не удалось подключиться к inference-серверу " + endpoint,
                    cause
            );
        }
        if (cause instanceof HttpTimeoutException) {
            return timeout(options, cause);
        }
        return new LlmRequestException(
                "Не удалось получить ответ inference-сервера: "
                        + Objects.toString(cause.getMessage(), cause.getClass().getSimpleName()),
                cause
        );
    }

    private LlmRequestException timeout(LlmRequestOptions options, Throwable cause) {
        return new LlmRequestException(
                "Inference-сервер не ответил за %d с".formatted(
                        Math.max(1, options.timeoutMillis() / 1000)
                ),
                cause
        );
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String stripped = value.strip();
        return stripped.length() > MAX_ERROR_BODY_LENGTH
                ? stripped.substring(0, MAX_ERROR_BODY_LENGTH) + "…"
                : stripped;
    }
}
