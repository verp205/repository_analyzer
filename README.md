# Repository Analyzer

Backend анализирует историю Java-репозитория, параллельно обрабатывает коммиты,
сохраняет технические результаты в SQLite и формирует ролевой отчёт через LLM.

## Запуск

Требуется JDK 21.

```bash
./mvnw spring-boot:run
```

После запуска доступны:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI: <http://localhost:8080/v3/api-docs>
- Health check: <http://localhost:8080/actuator/health>

По умолчанию включена локальная LLM-заглушка. Поэтому полный сценарий можно
проверить без API-ключа и inference-сервера.

## Быстрая проверка

Создать и запустить технический анализ:

```bash
curl -X POST http://localhost:8080/api/analysis-tasks \
  -H 'Content-Type: application/json' \
  -d '{
    "repositoryUrl":"https://github.com/spring-projects/spring-petclinic.git",
    "branch":"main"
  }'
```

Из ответа нужно сохранить `id`. Далее можно смотреть состояние и прогресс:

```bash
curl http://localhost:8080/api/analysis-tasks/1
curl http://localhost:8080/api/analysis-tasks/1/progress
```

Когда задача получит статус `COMPLETED`, доступны граф и группы изменений:

```bash
curl http://localhost:8080/api/analysis-tasks/1/graph
curl http://localhost:8080/api/analysis-tasks/1/clusters
```

Создать отчёт для разработчика:

```bash
curl -X POST http://localhost:8080/api/analysis-tasks/1/reports \
  -H 'Content-Type: application/json' \
  -d '{"audience":"DEVELOPER"}'
```

Допустимые аудитории: `DEVELOPER`, `ANALYST`, `SECURITY`, `TEAM_LEAD`,
`DIRECTOR`. Получить отчёт или подключиться к live-событиям:

```bash
curl http://localhost:8080/api/reports/1
curl -N http://localhost:8080/api/reports/1/events
```

Списки сохранённых задач и отчётов:

```bash
curl http://localhost:8080/api/analysis-tasks
curl http://localhost:8080/api/analysis-tasks/1/reports
```

## Многопоточность

`analysisExecutor` — один ограниченный пул максимум из 10 platform threads.
Worker-ы создаются `CommitWorkerFactory` и атомарно забирают случайные коммиты
через смену статуса `NEW -> PROCESSING`. Поэтому один коммит не должен быть
обработан двумя worker-ами.

JGit, построение diff и JavaParser работают параллельно. SQLite поддерживает
много читателей, но одного писателя, поэтому короткие операции записи проходят
через общий `SqliteWriteCoordinator`. Это не превращает весь анализ в
однопоточный: сериализуется только фиксация готового результата в базе.

LLM-клиент имеет отдельный `Semaphore`, ограничивающий число одновременно
выполняемых inference-запросов. `LlmRequestCoordinator` реализует single-flight:
одинаковый prompt, одновременно запрошенный разными потоками, отправляется в
LLM один раз, а остальные потоки ожидают общий результат. Затем ответ хранится
в SQLite по SHA-256 prompt-а.

## Настоящий inference-сервер

Поддерживается OpenAI-compatible endpoint `POST /v1/chat/completions`.
Параметры находятся в `application.properties`:

```properties
repository-analyzer.llm.enabled=true
repository-analyzer.llm.base-url=http://localhost:1234
repository-analyzer.llm.chat-path=/v1/chat/completions
repository-analyzer.llm.model=local-model
repository-analyzer.llm.api-key=
repository-analyzer.llm.max-concurrent-requests=2
```

## Проверка проекта

```bash
./mvnw test
```
