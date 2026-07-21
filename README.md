# Repository Analyzer

Анализатор истории Java-репозиториев с формированием отчёта через LLM.

## Как запустить

Понадобятся JDK 21, Node.js и npm.

1. Запустите backend из корня проекта:

   ```bash
   ./mvnw spring-boot:run
   ```

2. В новом окне терминала запустите frontend:

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

3. Откройте <http://localhost:5173>, вставьте ссылку на Git-репозиторий, укажите ветку, выберите вид отчёта и нажмите «Начать анализ».

По умолчанию используется тестовая LLM-заглушка, поэтому приложение запускается без API-ключа.

## Как поменять модель

Настройки находятся в `src/main/resources/application.properties`.

Для OpenAI или другого OpenAI-compatible сервера укажите:

```properties
repository-analyzer.llm.provider=openai-compatible
repository-analyzer.llm.provider-id=openai
repository-analyzer.llm.base-url=https://api.openai.com
repository-analyzer.llm.chat-path=/v1/chat/completions
repository-analyzer.llm.model=gpt-5.6-sol
repository-analyzer.llm.api-key=${LLM_API_KEY:}
repository-analyzer.llm.max-concurrent-requests=2
```

Чтобы выбрать другую модель, поменяйте значение:

```properties
repository-analyzer.llm.model=название-модели
```

API-ключ не сохраняйте в проекте. Перед запуском передайте его через переменную окружения:

```bash
export LLM_API_KEY=ваш-api-ключ
./mvnw spring-boot:run
```

После изменения настроек перезапустите backend.
