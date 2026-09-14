package com.ivan_naumenko.repository_analyzer.commitinsight.service;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.ChangeCategory;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDraft;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightStatus;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.RiskLevel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitInsightParserTest {

    private final CommitInsightParser parser =
            new CommitInsightParser(JsonMapper.builder().build());

    @Test
    void parsesJsonWrappedInReasoningAndMarkdown() {
        Optional<CommitInsightDraft> draft = parser.parseSingle("""
                <think>Сначала посмотрю на diff</think>
                ```json
                {
                  "summary": "Добавлена проверка токена",
                  "category": "security",
                  "riskLevel": "high",
                  "keyChanges": ["AuthFilter — проверка подписи"],
                  "behaviorImpact": "Запросы без токена отклоняются",
                  "risks": "Нет теста на истёкший токен",
                  "securityNotes": [],
                  "testNotes": "",
                  "checks": ["Проверить истёкший токен", "", "Проверить отзыв"]
                }
                ```
                """);

        assertTrue(draft.isPresent());
        assertEquals(CommitInsightStatus.ANALYZED, draft.get().status());
        assertEquals(ChangeCategory.SECURITY, draft.get().category());
        assertEquals(RiskLevel.HIGH, draft.get().riskLevel());
        assertEquals("Добавлена проверка токена", draft.get().summary());
        assertEquals(List.of("Нет теста на истёкший токен"), draft.get().details().risks());
        assertEquals(
                List.of("Проверить истёкший токен", "Проверить отзыв"),
                draft.get().details().checks()
        );
    }

    @Test
    void normalizesUnknownValuesAndLimitsLists() {
        CommitInsightDraft draft = parser.parseSingle("""
                {"summary":"Итог","category":"magic","riskLevel":"extreme",
                 "keyChanges":["1","2","3","4","5","6","7"]}
                """).orElseThrow();

        assertEquals(ChangeCategory.CHORE, draft.category());
        assertEquals(RiskLevel.UNKNOWN, draft.riskLevel());
        assertEquals(5, draft.details().keyChanges().size());
        assertEquals("", draft.details().behaviorImpact());
    }

    @Test
    void rejectsResponsesWithoutJsonOrSummary() {
        assertFalse(parser.parseSingle("Коммит добавляет README.").isPresent());
        assertFalse(parser.parseSingle("{\"summary\": \"\"}").isPresent());
        assertFalse(parser.parseSingle("{broken").isPresent());
        assertEquals(
                "Итог изменений",
                parser.fallbackSummary("<think>x</think> ## **Итог** изменений")
        );
    }

    @Test
    void parsesBatchByShortHash() {
        Map<String, CommitInsightDraft> drafts = parser.parseBatch("""
                {"commits":[
                  {"hash":"ABCDEF1234","summary":"Первый","category":"DOCS","riskLevel":"NONE"},
                  {"hash":"123","summary":"Короткий хэш игнорируется"},
                  {"hash":"1234567","summary":""},
                  {"hash":"fedcba9","summary":"Второй","category":"FIX","riskLevel":"LOW"}
                ]}
                """);

        assertEquals(2, drafts.size());
        assertEquals("Первый", drafts.get("abcdef1").summary());
        assertEquals(ChangeCategory.FIX, drafts.get("fedcba9").category());
    }
}
