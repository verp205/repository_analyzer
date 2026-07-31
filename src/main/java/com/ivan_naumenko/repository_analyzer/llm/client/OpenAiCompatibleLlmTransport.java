package com.ivan_naumenko.repository_analyzer.llm.client;

import com.ivan_naumenko.repository_analyzer.llm.client.config.LlmProperties;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmRequestException;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestOptions;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "repository-analyzer.llm",
        name = "provider",
        havingValue = "openai-compatible"
)
public class OpenAiCompatibleLlmTransport implements LlmTransport {

    private final LlmProperties properties;
    private final RestClient restClient;

    public OpenAiCompatibleLlmTransport(
            LlmProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .build();
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
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.model());
        request.put("messages", List.of(new ChatMessage("user", prompt)));
        request.put("temperature", 0.0);
        request.put("stream", false);
        request.put("max_tokens", options.maxTokens());
        if (options.reasoningEffort() != null) {
            request.put("reasoning_effort", options.reasoningEffort());
        }

        try {
            ChatCompletionResponse response = restClient.post()
                    .uri(properties.chatPath())
                    .headers(this::addAuthorization)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            if (response == null
                    || response.choices() == null
                    || response.choices().isEmpty()
                    || response.choices().getFirst().message() == null
                    || response.choices().getFirst().message().content() == null
                    || response.choices()
                    .getFirst()
                    .message()
                    .content()
                    .isBlank()) {
                throw new LlmRequestException(
                        "Inference-сервер вернул пустой ответ"
                );
            }

            return response.choices().getFirst().message().content();
        } catch (RestClientException exception) {
            throw new LlmRequestException(
                    "Не удалось получить ответ inference-сервера",
                    exception
            );
        }
    }

    private void addAuthorization(HttpHeaders headers) {
        if (properties.apiKey() != null
                && !properties.apiKey().isBlank()) {
            headers.setBearerAuth(properties.apiKey());
        }
    }

    private record ChatMessage(
            String role,
            String content
    ) {
    }

    private record ChatCompletionResponse(
            List<ChatChoice> choices
    ) {
    }

    private record ChatChoice(
            ChatMessage message
    ) {
    }
}
