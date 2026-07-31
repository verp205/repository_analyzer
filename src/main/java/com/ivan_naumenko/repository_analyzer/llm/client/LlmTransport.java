package com.ivan_naumenko.repository_analyzer.llm.client;

import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestOptions;

public interface LlmTransport {

    String cacheNamespace();

    String generate(String prompt);

    default String generate(String prompt, LlmRequestOptions options) {
        return generate(prompt);
    }
}
