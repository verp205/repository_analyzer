package com.ivan_naumenko.repository_analyzer.llm.prompt.service;

import com.ivan_naumenko.repository_analyzer.insight.cluster.dto.CommitClustersResponse;
import com.ivan_naumenko.repository_analyzer.insight.graph.dto.CommitGraphResponse;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSynthesisPromptFactoryTest {

    private final ProjectSynthesisPromptFactory factory =
            new ProjectSynthesisPromptFactory();

    @Test
    void requestsNineSynthesisSectionsAndForbidsTables() {
        String prompt = factory.buildFinal(
                report(ReportAudience.TEAM_LEAD),
                graph(),
                new CommitClustersResponse(1, 0, List.of()),
                List.of("commit summary")
        );

        assertTrue(prompt.contains("TASK_TYPE: PROJECT_SYNTHESIS_FINAL"));
        assertTrue(prompt.contains(
                "PROMPT_VERSION: project-synthesis-v3-project-overview"
        ));
        assertTrue(prompt.contains("## 1. Общая картина проекта"));
        assertTrue(prompt.contains("что это за проект"));
        assertTrue(prompt.contains("для кого предназначен"));
        assertTrue(prompt.contains("что умеет в целом"));
        assertTrue(prompt.contains(
                "по доступным изменениям можно сделать вывод"
        ));
        assertTrue(prompt.contains("## 9. Ограничения анализа"));
        assertTrue(prompt.contains("Не добавляй раздел 10"));
        assertTrue(prompt.contains("Никогда не используй Markdown-таблицы"));
        assertFalse(prompt.contains("|---|"));
    }

    @Test
    void createsDifferentFinalPromptsForDifferentAudiences() {
        String developer = factory.buildFinal(
                report(ReportAudience.DEVELOPER),
                graph(),
                new CommitClustersResponse(1, 0, List.of()),
                List.of("summary")
        );
        String director = factory.buildFinal(
                report(ReportAudience.DIRECTOR),
                graph(),
                new CommitClustersResponse(1, 0, List.of()),
                List.of("summary")
        );

        assertNotEquals(developer, director);
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

    private CommitGraphResponse graph() {
        return new CommitGraphResponse(
                1,
                3,
                1,
                1,
                0,
                3,
                0,
                List.of(),
                List.of()
        );
    }
}
