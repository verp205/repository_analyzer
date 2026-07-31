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
        return "stub:v2";
    }

    @Override
    public String generate(String prompt) {
        String hash = promptHashService.calculate(prompt);

        if (prompt.contains("TASK_TYPE: PROJECT_SYNTHESIS_FINAL")) {
            return """
                    ## 1. Общая картина проекта

                    История показывает последовательное развитие репозитория. Это
                    демонстрационная сводка: реальная модель сформирует выводы по
                    доступным анализам коммитов и техническим метрикам.

                    ## 2. Основные направления развития

                    - Развитие функциональности и технической инфраструктуры.
                    - Уточнение контрактов и повышение управляемости изменений.

                    ## 3. Эволюция архитектуры

                    Доступные данные переданы в этап общего синтеза. Для точных
                    архитектурных выводов необходимо подключить inference-сервер.

                    ## 4. Что сделано хорошо

                    - Анализ коммитов разделён на независимые задачи.
                    - Ответы кэшируются и собираются в хронологическом порядке.

                    ## 5. Повторяющиеся проблемы и риски

                    Демонстрационный transport не выполняет реальный поиск рисков.

                    ## 6. Незавершённые направления

                    Требуется подключить настоящую LLM-модель для смыслового анализа.

                    ## 7. Наиболее значимые изменения

                    Значимые изменения будут выбраны моделью на основании сводок.

                    ## 8. Рекомендации для выбранной роли

                    Проверьте настройки inference-сервера и полноту переданного контекста.

                    ## 9. Ограничения анализа

                    Ответ сформирован демонстрационным transport и не является
                    содержательным аудитом репозитория.

                    Prompt hash: `%s`
                    """.formatted(hash);
        }

        if (prompt.contains("TASK_TYPE: PROJECT_SYNTHESIS_BATCH")) {
            return """
                    - Сводка группы коммитов подготовлена для итогового синтеза.
                    - Фактический смысловой анализ появится после подключения LLM.
                    - Ограничение: используется демонстрационный transport.

                    Prompt hash: `%s`
                    """.formatted(hash);
        }

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
