package com.ivan_naumenko.repository_analyzer.report.grounding;

import com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.Citation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

/** Validates model selections against server-owned code excerpts. The model never supplies quote text. */
@Service
@RequiredArgsConstructor
public class GroundedReviewParser {
    private final ObjectMapper mapper;
    public record Item(String title, String impact, String riskLevel, String risk, String action, String verification,
                       List<Citation> citations, List<String> unknowns) {}
    public record Review(List<Item> items, List<String> unknowns) {}
    public record Verdict(boolean supported, List<String> issues) {}

    public Verdict verdict(String raw) {
        if (raw == null || raw.length() > 12_000) throw invalid("Ответ смысловой проверки отсутствует или слишком велик");
        String cleaned = raw.replaceAll("(?is)<think>.*?</think>", "").strip();
        JsonNode root;
        try { root = mapper.readTree(cleaned.substring(cleaned.indexOf('{'), cleaned.lastIndexOf('}') + 1)); }
        catch (RuntimeException error) { throw invalid("Некорректный JSON смысловой проверки"); }
        if (root == null || !root.path("supported").isBoolean()) throw invalid("Отсутствует результат смысловой проверки");
        List<String> issues = strings(root.path("issues"), 4);
        boolean supported = root.path("supported").booleanValue();
        if (supported != issues.isEmpty()) throw invalid("Противоречивый результат смысловой проверки");
        return new Verdict(supported, issues);
    }

    public Review parse(String raw, ReviewContext context, String originalRisk) {
        if (raw == null || raw.length() > 40_000) throw invalid("Ответ отсутствует или слишком велик");
        String cleaned = raw.replaceAll("(?is)<think>.*?</think>", "").strip();
        JsonNode root;
        try { root = mapper.readTree(cleaned.substring(cleaned.indexOf('{'), cleaned.lastIndexOf('}') + 1)); }
        catch (RuntimeException exception) { throw invalid("Нужен корректный JSON"); }
        if (root == null || !root.isObject()) throw invalid("Нужен объект JSON");
        var byId = new HashMap<String, Citation>(); context.citations().forEach(c -> byId.put(c.id(), c));
        var items = new ArrayList<Item>();
        Set<String> duplicates = new HashSet<>();
        for (var node : array(root.path("findings"), 3)) {
            String title = text(node.path("title"), 220, false);
            if (!duplicates.add(title.toLowerCase(Locale.ROOT))) throw invalid("Повторяющийся вывод");
            String impact = text(node.path("impact"), 650, false);
            String risk = text(node.path("risk"), 500, true);
            String level = text(node.path("riskLevel"), 10, false);
            String mechanism = text(node.path("riskMechanism"), 25, false);
            if (!Set.of("NONE", "LOW", "MEDIUM", "HIGH").contains(level)) throw invalid("Неизвестный уровень риска");
            if (!Set.of("NONE", "BEHAVIOR", "ACCESS", "SECRETS", "DATA_LOSS", "MEMORY_SAFETY", "INPUT_VALIDATION").contains(mechanism))
                throw invalid("Нужен конкретный механизм риска");
            List<Citation> citations = references(node.path("sourceIds"), byId);
            if (citations.stream().noneMatch(c -> c.side().equals("DIFF") && changedLines(c.code())))
                throw invalid("Вывод должен ссылаться на фрагмент diff с изменёнными строками");
            List<String> mitigationIds = strings(node.path("mitigationIds"), 4);
            if (mitigationIds.stream().anyMatch(id -> !byId.containsKey(id)
                    || !(Set.of("AFTER", "RELATED").contains(byId.get(id).side())
                    || byId.get(id).side().equals("DIFF") && byId.get(id).code().lines().anyMatch(line -> line.startsWith("+") && !line.startsWith("+++")))))
                throw invalid("Защитный механизм должен быть показан в коде после изменения");
            if (Set.of("ACCESS", "SECRETS", "DATA_LOSS", "MEMORY_SAFETY").contains(mechanism)
                    && !level.equals("HIGH") && mitigationIds.isEmpty())
                throw invalid("Опасный механизм без показанной защиты несовместим с пониженным уровнем риска");
            if (originalRisk.equals("HIGH") && !level.equals("HIGH") && mitigationIds.isEmpty())
                throw invalid("Для снижения прежнего HIGH нужны цитаты защитного механизма");
            if (level.equals("NONE") && (!risk.isEmpty() || !mechanism.equals("NONE")))
                throw invalid("NONE несовместим с описанным риском");
            if (!level.equals("NONE") && (risk.isEmpty() || mechanism.equals("NONE")))
                throw invalid("У риска должны быть основание и механизм");
            String action = text(node.path("action"), 300, true);
            String verification = text(node.path("verification"), 600, true);
            if ((!level.equals("NONE") && action.isEmpty()) || (!action.isEmpty() && verification.length() < 25))
                throw invalid("Нужны действие и конкретный сценарий с ожидаемым результатом");
            if (Set.of("проверить тестами", "добавить тесты", "проверить результат", "проверить код").contains(verification.toLowerCase(Locale.ROOT)))
                throw invalid("Проверка слишком общая");
            String combined = (title + " " + impact + " " + risk).toLowerCase(Locale.ROOT);
            if (combined.contains("[подтверждено]") || combined.contains("тесты прошли") || combined.contains("тесты успешно")
                    || combined.contains("эксплуатация подтверждена")) throw invalid("Нет результатов выполнения кода или тестов");
            var allCitations = new LinkedHashMap<String, Citation>();
            citations.forEach(c -> allCitations.put(c.id(), c));
            mitigationIds.forEach(id -> allCitations.put(id, byId.get(id)));
            items.add(new Item(title, impact, level, risk, action, verification, List.copyOf(allCitations.values()), strings(node.path("unknowns"), 4)));
        }
        List<String> unknowns = strings(root.path("unknowns"), 5);
        if (items.isEmpty() && unknowns.isEmpty()) throw invalid("Пустой разбор должен объяснить, чего не удалось установить");
        return new Review(List.copyOf(items), unknowns);
    }

    private List<Citation> references(JsonNode node, Map<String, Citation> sources) {
        List<String> ids = strings(node, 6);
        if (ids.isEmpty() || ids.stream().anyMatch(id -> !sources.containsKey(id))) throw invalid("Ссылка на отсутствующий фрагмент кода");
        return ids.stream().map(sources::get).toList();
    }
    private List<String> strings(JsonNode node, int limit) {
        List<String> result = new ArrayList<>();
        for (var item : array(node, limit)) result.add(text(item, 450, false));
        return result.stream().distinct().toList();
    }
    private JsonNode array(JsonNode node, int limit) {
        if (!node.isArray() || node.size() > limit) throw invalid("Некорректный или слишком длинный массив");
        return node;
    }
    private String text(JsonNode node, int limit, boolean empty) {
        if (!node.isString() || node.stringValue().length() > limit || !empty && node.stringValue().isBlank()) throw invalid("Недопустимое текстовое поле");
        String cleaned = node.stringValue().replaceAll("!?\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("<[^>]*>", "").replaceAll("[\\r\\n]+", " ").strip();
        if (!empty && cleaned.isBlank()) throw invalid("Текст пуст после удаления оформления");
        return cleaned;
    }
    private boolean changedLines(String code) {
        return code.lines().anyMatch(line -> line.startsWith("+") && !line.startsWith("+++")
                || line.startsWith("-") && !line.startsWith("---"));
    }
    private IllegalStateException invalid(String reason) { return new IllegalStateException("Проверка по коду: " + reason); }
}
