package com.ivan_naumenko.repository_analyzer.llm.prompt.service;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.ChangeCategory;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import com.ivan_naumenko.repository_analyzer.report.snapshot.RepositorySnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSynthesisPromptFactoryTest {

    private final ProjectSynthesisPromptFactory factory =
            new ProjectSynthesisPromptFactory();

    @Test
    void buildsCompactSecurityReportWithFactsAndCodeState() {
        String prompt = factory.buildFinal(
                report(ReportAudience.SECURITY),
                facts(),
                snapshot(),
                List.of("commit summary")
        );

        assertTrue(prompt.contains("TASK_TYPE: PROJECT_SYNTHESIS_FINAL"));
        assertTrue(prompt.contains("Период отчёта: вся история"));
        assertTrue(prompt.contains("Строки «PR» и «Задачи»"));
        assertTrue(prompt.contains(
                "PROMPT_VERSION: " + ProjectSynthesisPromptFactory.PROMPT_VERSION
        ));
        assertTrue(prompt.contains("ЧИТАТЕЛЬ: специалист по информационной безопасности"));
        assertTrue(prompt.contains("Каждая мысль звучит в отчёте один раз"));
        assertTrue(prompt.contains("Весь отчёт — не больше 350 слов"));
        assertTrue(prompt.contains("## Главное"));
        assertTrue(prompt.contains("## Система и точки входа"));
        assertTrue(prompt.contains("## Находки"));
        assertTrue(prompt.contains("**[Подтверждено]**"));
        assertTrue(prompt.contains("## Ограничения анализа"));
        assertTrue(prompt.contains("- Период: 2026-07-01 — 2026-07-20 (дней: 20, активных дней: 8)"));
        assertTrue(prompt.contains("- Коммитов: 12; слияний: 1; без смыслового анализа: 2"));
        assertTrue(prompt.contains("Чаще всего изменялись: src/App.java (коммитов: 5)"));
        assertTrue(prompt.contains("Риск в коммитах: высокий 1, средний 2"));
        assertTrue(prompt.contains("- Описание из README: Учёт товаров на складах"));
        assertTrue(prompt.contains("- Spring Security в зависимостях: не обнаружен"));
        assertTrue(prompt.contains("CORS разрешает запросы с любых источников"));
        assertTrue(prompt.contains("src/main/resources/application.yml: spring.datasource.password"));
        assertTrue(prompt.contains("(a1b2c3d)"));
        assertFalse(prompt.contains("Ivan (коммитов: 9)"));
        assertFalse(prompt.contains("Новая функциональность — 6"));
        assertFalse(prompt.contains("|---|"));
        assertFalse(prompt.contains("## Риски и что сделать"));
    }

    @Test
    void adaptsSectionsAndFactsToReader() {
        String developer = factory.buildFinal(
                report(ReportAudience.DEVELOPER),
                facts(),
                snapshot(),
                List.of("summary")
        );
        String director = factory.buildFinal(
                report(ReportAudience.DIRECTOR),
                facts(),
                snapshot(),
                List.of("summary")
        );
        String teamLead = factory.buildFinal(
                report(ReportAudience.TEAM_LEAD),
                facts(),
                snapshot(),
                List.of("summary")
        );
        String analyst = factory.buildFinal(
                report(ReportAudience.ANALYST),
                facts(),
                snapshot(),
                List.of("summary")
        );

        assertNotEquals(developer, director);
        assertTrue(developer.contains("## Риски и что сделать"));
        assertTrue(developer.contains("Файлов кода: основных 10, тестовых 2"));
        assertTrue(director.contains("## Риски и решения"));
        assertTrue(director.contains("без жаргона"));
        assertTrue(director.contains("Весь отчёт — не больше 245 слов"));
        assertFalse(director.contains("## Риски и что сделать"));
        assertFalse(director.contains("HTTP-контроллеры"));
        assertTrue(teamLead.contains("Ivan (коммитов: 9)"));
        assertFalse(analyst.contains("Spring Security"));
        assertTrue(analyst.contains("HTTP-контроллеры: InventoryController"));
    }

    @Test
    void reportsMissingCodeState() {
        String prompt = factory.buildFinal(
                report(ReportAudience.SECURITY),
                facts(),
                RepositorySnapshot.unavailable(),
                List.of("summary")
        );

        assertTrue(prompt.contains("- Недоступно: локальная копия репозитория не найдена."));
    }

    @Test
    void intermediatePromptKeepsReaderFocus() {
        String prompt = factory.buildIntermediate(
                report(ReportAudience.ANALYST),
                1,
                0,
                2,
                List.of("source")
        );

        assertTrue(prompt.contains("TASK_TYPE: PROJECT_SYNTHESIS_BATCH"));
        assertTrue(prompt.contains("BATCH: 1/2"));
        assertTrue(prompt.contains("бизнес-правила"));
        assertTrue(prompt.contains("объединяй в один пункт"));
    }

    private ReportGenerationData report(ReportAudience audience) {
        return new ReportGenerationData(
                10,
                1,
                audience,
                "https://example.com/project.git",
                "main"
        );
    }

    private RepositorySnapshot snapshot() {
        return new RepositorySnapshot(
                true,
                "Учёт товаров на складах",
                List.of("Maven", "Spring Boot 3.3.1", "PostgreSQL"),
                List.of(new RepositorySnapshot.ModuleFact("controller", 2)),
                10,
                2,
                List.of("InventoryController"),
                new RepositorySnapshot.SecurityFacts(
                        false,
                        false,
                        0,
                        false,
                        true,
                        false,
                        List.of("src/main/resources/application.yml: spring.datasource.password"),
                        List.of()
                ),
                false
        );
    }

    private RepositoryFactsResponse facts() {
        return new RepositoryFactsResponse(
                1,
                "https://example.com/project.git",
                "project",
                "main",
                Instant.parse("2026-07-01T10:00:00Z"),
                Instant.parse("2026-07-20T10:00:00Z"),
                20,
                8,
                12,
                12,
                0,
                1,
                1_000,
                200,
                40,
                2,
                List.of(
                        new RepositoryFactsResponse.AuthorFact("Ivan", 9, 800, 150, 75.0),
                        new RepositoryFactsResponse.AuthorFact("Anna", 3, 200, 50, 25.0)
                ),
                "DAY",
                List.of(),
                List.of(new RepositoryFactsResponse.HotspotFact("src/App.java", 5, 100, 20)),
                List.of(new RepositoryFactsResponse.FileTypeFact("java", 30, 60)),
                List.of(),
                new RepositoryFactsResponse.RiskDistribution(1, 2, 5, 2, 0),
                List.of(new RepositoryFactsResponse.CategoryFact(
                        ChangeCategory.FEATURE,
                        ChangeCategory.FEATURE.displayName(),
                        6
                )),
                10
        );
    }
}
