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


## Проверка проекта

```bash
./mvnw test
```
