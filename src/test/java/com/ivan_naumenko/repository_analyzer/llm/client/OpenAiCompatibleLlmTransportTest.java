package com.ivan_naumenko.repository_analyzer.llm.client;

import com.ivan_naumenko.repository_analyzer.llm.client.config.LlmProperties;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestOptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleLlmTransportTest {

    @Test
    void sendsOutputAndReasoningLimitsToOpenAiCompatibleServer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder)
                .build();
        OpenAiCompatibleLlmTransport transport =
                new OpenAiCompatibleLlmTransport(properties(), builder);

        server.expect(once(), requestTo(
                        "http://localhost:11434/v1/chat/completions"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("\"max_tokens\":700"),
                        containsString("\"reasoning_effort\":\"none\""),
                        containsString("\"stream\":false")
                )))
                .andRespond(withSuccess(
                        """
                                {"choices":[{"message":{
                                  "role":"assistant","content":"Готово"
                                }}]}
                                """,
                        MediaType.APPLICATION_JSON
                ));

        String result = transport.generate(
                "Короткий prompt",
                new LlmRequestOptions(700, "none")
        );

        assertEquals("Готово", result);
        server.verify();
    }

    private LlmProperties properties() {
        return new LlmProperties(
                "openai-compatible",
                "ollama",
                "http://localhost:11434",
                "/v1/chat/completions",
                "qwen3:8b",
                "",
                4,
                700,
                "none",
                600,
                "none",
                1_800,
                "low"
        );
    }
}
