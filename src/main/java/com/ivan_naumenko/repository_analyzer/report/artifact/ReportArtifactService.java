package com.ivan_naumenko.repository_analyzer.report.artifact;

import com.ivan_naumenko.repository_analyzer.commitinsight.dto.CommitInsightResponse;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightStatus;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;
import com.ivan_naumenko.repository_analyzer.report.model.AudienceProfile;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import com.ivan_naumenko.repository_analyzer.report.snapshot.RepositorySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.*;

@Service
@RequiredArgsConstructor
public class ReportArtifactService {
    private final ObjectMapper mapper;
    private static final Pattern THINK = Pattern.compile("(?is)<think>.*?</think>");
    private static final Pattern LINK = Pattern.compile("!?\\[([^]]*)]\\([^)]*\\)");
    private static final Pattern HASH = Pattern.compile("(?:\\(|`)([a-fA-F0-9]{7,40})(?:\\)|`)");

    public List<Evidence> catalog(ReportGenerationData report, RepositoryFactsResponse facts,
                                  RepositorySnapshot snapshot, List<CommitChangeSummary> commits,
                                  List<CommitInsightResponse> insights) {
        List<Evidence> result = new ArrayList<>();
        result.add(new Evidence("F1", "FACTS", null, report.headCommitHash(), "Показатели за период",
                List.of(), mapper.writeValueAsString(facts)));
        if (snapshot != null && snapshot.available()) {
            List<String> paths = snapshot.sourcePaths();
            result.add(new Evidence("S1", "SNAPSHOT", null, report.headCommitHash(), "Состояние кода при создании отчёта",
                    paths, mapper.writeValueAsString(snapshot)));
        }
        Set<Integer> useful = insights.stream().filter(i -> i.status() != CommitInsightStatus.FAILED)
                .map(CommitInsightResponse::commitId).collect(Collectors.toSet());
        for (CommitChangeSummary commit : commits) {
            if (!useful.contains(commit.commitId())) continue;
            result.add(new Evidence("C" + commit.commitId(), "COMMIT", commit.commitId(), commit.commitHash(),
                    commit.message() == null ? commit.commitHash() : commit.message().lines().findFirst().orElse("Коммит"),
                    commit.files().stream().map(f -> f.path()).distinct().toList(),
                    "Коммит " + commit.commitHash() + " · " + commit.committedAt() + " · +" + commit.addedLines() + " / -" + commit.deletedLines()));
        }
        return List.copyOf(result);
    }

    public String promptCatalog(List<Evidence> evidence, List<String> sources) {
        Set<String> referenced = Pattern.compile("\\bC[0-9]+\\b").matcher(String.join("\n", sources))
                .results().map(match -> match.group()).collect(Collectors.toSet());
        return evidence.stream().filter(e -> !"COMMIT".equals(e.kind()) || referenced.contains(e.id()))
                .map(e -> e.id() + " | " + e.kind() + " | "
                + e.label().substring(0, Math.min(300, e.label().length())).replace("</", "< /") + " | " + e.commitHash() + " | " + String.join(", ", e.paths().stream().filter(path -> path.length() <= 300).limit(15).toList()))
                .collect(Collectors.joining("\n"));
    }

    public SynthesisResult parse(String raw, ReportGenerationData report, RepositoryFactsResponse facts,
                                 List<Evidence> evidence) {
        if (raw == null || raw.length() > 120_000) throw invalid("Пустой или слишком большой ответ модели");
        String cleaned = THINK.matcher(raw).replaceAll("").strip();
        int start = cleaned.indexOf('{'), end = cleaned.lastIndexOf('}');
        JsonNode root;
        try {
            root = mapper.readTree(cleaned.substring(start, end + 1));
        } catch (RuntimeException ex) {
            throw invalid("Модель вернула некорректный JSON итогового отчёта");
        }
        if (root == null || !root.isObject()) throw invalid("Ожидался объект итогового отчёта");
        validateMetrics(root.path("metrics"), facts);
        Map<String, Evidence> sources = evidence.stream().collect(Collectors.toMap(Evidence::id, e -> e));
        List<Finding> summary = findings(root.path("summary"), sources, 5);
        JsonNode sectionNodes = array(root.path("sections"), 8, "sections");
        List<String> expectedTitles = AudienceProfile.of(report.audience()).sections().stream()
                .map(s -> s.title().replaceFirst("^##\\s*", "")).toList();
        if (sectionNodes.size() != expectedTitles.size()) throw invalid("Неверная структура разделов для выбранной роли");
        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < sectionNodes.size(); i++) {
            JsonNode node = sectionNodes.path(i);
            String title = text(node.path("title"), 120).replaceFirst("^##\\s*", "");
            if (!title.equals(expectedTitles.get(i))) throw invalid("Неожиданный раздел: " + title);
            sections.add(new Section(title, findings(node.path("findings"), sources, 8)));
        }
        List<Action> actions = new ArrayList<>();
        JsonNode actionNodes = array(root.path("actions"), 10, "actions");
        for (int i = 0; i < actionNodes.size(); i++) {
            JsonNode node = actionNodes.path(i);
            List<String> ids = references(node.path("evidenceIds"), sources);
            List<String> paths = strings(node.path("paths"), 15);
            Set<String> allowedPaths = ids.stream().flatMap(id -> sources.get(id).paths().stream()).collect(Collectors.toSet());
            if (paths.isEmpty() || !allowedPaths.containsAll(paths)) throw invalid("План действий ссылается на неизвестный файл");
            String priority = text(node.path("priority"), 10);
            if (!Set.of("HIGH", "MEDIUM", "LOW").contains(priority)) throw invalid("Неизвестный приоритет задачи");
            String title = text(node.path("title"), 300);
            String reason = text(node.path("reason"), 1200);
            String verification = text(node.path("verification"), 1200);
            validateHashes(title + " " + reason + " " + verification, sources);
            validateNumbers(title + " " + reason + " " + verification, sources);
            if (Set.of("проверить тестами", "добавить тесты", "проверить результат", "проверить").contains(verification.toLowerCase()))
                throw invalid("Задаче нужен конкретный сценарий проверки");
            actions.add(new Action("A" + (i + 1), title, reason, verification, priority, ids, paths));
        }
        if (facts.risks() != null && facts.risks().high() > 0
                && evidence.stream().anyMatch(source -> "COMMIT".equals(source.kind()) && !source.paths().isEmpty())
                && actions.stream().noneMatch(action -> action.evidenceIds().stream()
                .anyMatch(id -> "COMMIT".equals(sources.get(id).kind())))) {
            throw invalid("При выявленном HIGH-риске нужна задача с конкретной проверкой и файлом из источника COMMIT");
        }
        List<String> limitations = strings(root.path("limitations"), 5);
        limitations.forEach(value -> { validateHashes(value, sources); validateNumbers(value, sources); });
        StringBuilder markdown = new StringBuilder("## Главное\n\n");
        appendFindings(markdown, summary);
        for (Section section : sections) {
            markdown.append("\n## ").append(section.title()).append("\n\n");
            appendFindings(markdown, section.findings());
        }
        markdown.append("\n## Ограничения анализа\n\n");
        if (limitations.isEmpty()) markdown.append("Смысловые выводы модели требуют проверки по исходному коду.\n");
        else limitations.forEach(value -> markdown.append("- ").append(value).append('\n'));
        int count = summary.size() + sections.stream().mapToInt(s -> s.findings().size()).sum();
        Quality quality = new Quality("CHECKED", count, actions.size(), List.of(
                "Пять числовых показателей JSON совпадают с рассчитанными фактами",
                "Источники принадлежат выбранному периоду отчёта",
                "Каждый вывод и задача имеют существующие источники",
                "Файлы задач присутствуют в указанных источниках",
                "Структура соответствует выбранной роли"), java.util.stream.Stream.concat(limitations.stream(), java.util.stream.Stream.of(
                "Наличие ссылки подтверждает источник, но не истинность интерпретации модели. Проверьте выводы по diff.")).distinct().toList());
        return new SynthesisResult(markdown.toString(), new ReportArtifacts(summary, List.copyOf(sections),
                List.copyOf(actions), evidence, quality));
    }

    private void validateMetrics(JsonNode metrics, RepositoryFactsResponse facts) {
        if (facts == null) throw invalid("Отсутствуют рассчитанные показатели отчёта");
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("totalCommits", facts.totalCommits());
        expected.put("authorCount", facts.authorCount());
        expected.put("touchedFiles", facts.touchedFiles());
        expected.put("addedLines", facts.addedLines());
        expected.put("deletedLines", facts.deletedLines());
        expected.forEach((key, value) -> {
            JsonNode node = metrics.path(key);
            if (!node.isIntegralNumber() || node.longValue() != value) throw invalid("Недостоверный показатель: " + key);
        });
    }

    private List<Finding> findings(JsonNode nodes, Map<String, Evidence> sources, int limit) {
        nodes = array(nodes, limit, "findings");
        List<Finding> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            String value = text(node.path("text"), 1600);
            validateHashes(value, sources);
            validateNumbers(value, sources);
            result.add(new Finding(value, references(node.path("evidenceIds"), sources)));
        }
        return List.copyOf(result);
    }

    private void validateHashes(String text, Map<String, Evidence> sources) {
        var matcher = HASH.matcher(text);
        while (matcher.find()) {
            String hash = matcher.group(1).toLowerCase();
            long matches = sources.values().stream().filter(e -> "COMMIT".equals(e.kind()) && e.commitHash().startsWith(hash)).count();
            if (matches != 1) throw invalid("Неизвестный или неоднозначный коммит в тексте: " + hash);
        }
    }

    private void validateNumbers(String value, Map<String, Evidence> sources) {
        JsonNode facts = mapper.readTree(sources.get("F1").description());
        var labels = Pattern.compile("(?iu)(коммит(?:ов|ы)?|автор(?:ов|ы)?|(?:измен[её]нных\\s+)?файлов|добавлено\\s+строк|удалено\\s+строк)\\s*[:—-]\\s*(\\d+)").matcher(value);
        while (labels.find()) compareMetric(labels.group(1), labels.group(2), facts);
        var totals = Pattern.compile("(?iu)(?:всего|за\\s+период|итого)\\s+(\\d+)\\s+(коммит\\p{L}*|автор\\p{L}*|файл\\p{L}*)").matcher(value);
        while (totals.find()) compareMetric(totals.group(2), totals.group(1), facts);
    }

    private void compareMetric(String label, String number, JsonNode facts) {
        label = label.toLowerCase();
        String key = label.startsWith("коммит") ? "totalCommits" : label.startsWith("автор") ? "authorCount"
                : label.startsWith("добавлено") ? "addedLines" : label.startsWith("удалено") ? "deletedLines" : "touchedFiles";
        try {
            if (Long.parseLong(number) != facts.path(key).longValue()) throw invalid("Недостоверное число в тексте: " + label);
        } catch (NumberFormatException exception) { throw invalid("Некорректное число в тексте"); }
    }

    private List<String> references(JsonNode node, Map<String, Evidence> sources) {
        List<String> ids = strings(node, 10);
        if (ids.isEmpty() || ids.stream().anyMatch(id -> !sources.containsKey(id))) throw invalid("Вывод ссылается на отсутствующий источник");
        return ids;
    }

    private List<String> strings(JsonNode node, int limit) {
        node = array(node, limit, "list");
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) result.add(text(item, 1600));
        return result.stream().distinct().toList();
    }

    private JsonNode array(JsonNode node, int limit, String field) {
        if (!node.isArray() || node.size() > limit) throw invalid("Некорректное поле " + field);
        return node;
    }

    private String text(JsonNode node, int limit) {
        if (!node.isString() || node.stringValue().isBlank() || node.stringValue().length() > limit) throw invalid("Пустой или слишком длинный текст");
        String cleaned = LINK.matcher(node.stringValue()).replaceAll("$1").replaceAll("<[^>]*>", "")
                .replaceAll("(?m)^#{1,6}\\s*", "").strip();
        if (cleaned.isBlank()) throw invalid("Текст пуст после удаления оформления");
        return cleaned;
    }

    private void appendFindings(StringBuilder target, List<Finding> findings) {
        if (findings.isEmpty()) target.append("Существенных выводов по этому разделу нет.\n");
        for (Finding finding : findings) {
            target.append("- ").append(finding.text().replace("\n", " ")).append(' ');
            finding.evidenceIds().forEach(id -> target.append('[').append(id).append("](#evidence-").append(id).append(") "));
            target.append('\n');
        }
    }

    private IllegalStateException invalid(String message) {
        return new IllegalStateException("Проверка итогового отчёта: " + message);
    }
}
