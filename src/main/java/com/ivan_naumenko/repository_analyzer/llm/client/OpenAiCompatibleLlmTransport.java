package com.ivan_naumenko.repository_analyzer.llm.client;

import com.ivan_naumenko.repository_analyzer.llm.client.config.LlmProperties;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmRequestException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "repository-analyzer.llm",
        name = "enabled",
        havingValue = "true"
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
    public String generate(String prompt) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                properties.model(),
                List.of(new ChatMessage("user", prompt)),
                0.0
        );

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

    private record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            double temperature
    ) {
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
