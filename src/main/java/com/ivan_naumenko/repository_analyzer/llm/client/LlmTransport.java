package com.ivan_naumenko.repository_analyzer.llm.client;

public interface LlmTransport {

    String generate(String prompt);
}
