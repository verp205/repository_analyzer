package com.ivan_naumenko.repository_analyzer.llm.client;

import com.ivan_naumenko.repository_analyzer.llm.prompt.service.PromptHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "repository-analyzer.llm",
        name = "provider",
        havingValue = "stub",
        matchIfMissing = true
)
public class StubLlmTransport implements LlmTransport {

    private final PromptHashService promptHashService;

    @Override
    public String cacheNamespace() {
        return "stub:v1";
    }

    @Override
    public String generate(String prompt) {
        String hash = promptHashService.calculate(prompt);

        return """
                ### Демонстрационный LLM-ответ

                Реальный inference-сервер пока отключён. Backend успешно:

                - сформировал компактный контекст коммита;
                - создал ролевой prompt;
                - выполнил single-flight координацию;
                - подготовил ответ к сохранению в SQLite-кэше.

                Prompt hash: `%s`

                Для настоящего смыслового анализа включите
                `repository-analyzer.llm.provider=openai-compatible` и укажите параметры
                OpenAI-compatible inference-сервера.
                """.formatted(hash);
    }
}
