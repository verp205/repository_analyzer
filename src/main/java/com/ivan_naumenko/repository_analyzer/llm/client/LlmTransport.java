package com.ivan_naumenko.repository_analyzer.llm.client;

public interface LlmTransport {

    String cacheNamespace();

    String generate(String prompt);
}
