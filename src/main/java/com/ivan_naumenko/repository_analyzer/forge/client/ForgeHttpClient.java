package com.ivan_naumenko.repository_analyzer.forge.client;

import com.ivan_naumenko.repository_analyzer.forge.config.ForgeProperties;
import com.ivan_naumenko.repository_analyzer.forge.exception.ForgeRateLimitException;
import com.ivan_naumenko.repository_analyzer.forge.exception.ForgeRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;

/** GET-запросы к REST API GitHub/GitLab с распознаванием лимитов. */
@Component
public class ForgeHttpClient {

    private static final Duration DEFAULT_RATE_LIMIT_PAUSE = Duration.ofMinutes(10);

    private final ForgeProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    @Autowired
    public ForgeHttpClient(ForgeProperties properties, ObjectMapper mapper) {
        this(
                properties,
                mapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(properties.requestTimeoutMillis()))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build()
        );
    }

    public ForgeHttpClient(ForgeProperties properties, ObjectMapper mapper, HttpClient httpClient) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    /** Возвращает JSON ответа или пустой результат, если ресурс не найден. */
    public Optional<JsonNode> getJson(URI uri, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(properties.requestTimeoutMillis()))
                .header("User-Agent", "repository-analyzer")
                .GET();
        headers.forEach(builder::header);

        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Запрос к API хостинга прерван");
        } catch (IOException exception) {
            throw new ForgeRequestException(
                    "Не удалось выполнить запрос к " + uri.getHost() + ": " + exception.getMessage(),
                    exception
            );
        }

        int status = response.statusCode();
        if (status == 404) {
            return Optional.empty();
        }
        if (status == 429 || (status == 403 && rateLimitExhausted(response))) {
            throw new ForgeRateLimitException(
                    "Лимит запросов к " + uri.getHost() + " исчерпан",
                    retryAt(response)
            );
        }
        if (status == 401 || status == 403) {
            throw new ForgeRequestException(
                    "Нет доступа к API " + uri.getHost() + " (HTTP " + status + "): проверьте токен"
            );
        }
        if (status / 100 != 2) {
            throw new ForgeRequestException("API " + uri.getHost() + " вернуло HTTP " + status);
        }

        try {
            return Optional.ofNullable(mapper.readTree(response.body()));
        } catch (RuntimeException exception) {
            throw new ForgeRequestException("Некорректный JSON от " + uri.getHost(), exception);
        }
    }

    private boolean rateLimitExhausted(HttpResponse<?> response) {
        return "0".equals(header(response, "x-ratelimit-remaining"))
                || "0".equals(header(response, "ratelimit-remaining"))
                || header(response, "retry-after") != null;
    }

    private Instant retryAt(HttpResponse<?> response) {
        String reset = firstNonNull(
                header(response, "x-ratelimit-reset"),
                header(response, "ratelimit-reset")
        );
        if (reset != null) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(reset.strip()));
            } catch (NumberFormatException ignored) {
                // Формат не распознан — используем паузу по умолчанию.
            }
        }
        String retryAfter = header(response, "retry-after");
        if (retryAfter != null) {
            try {
                return Instant.now().plusSeconds(Long.parseLong(retryAfter.strip()));
            } catch (NumberFormatException ignored) {
                // Дата в формате HTTP — используем паузу по умолчанию.
            }
        }
        return Instant.now().plus(DEFAULT_RATE_LIMIT_PAUSE);
    }

    private String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    private String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }
}
