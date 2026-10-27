package com.ivan_naumenko.repository_analyzer.report.grounding;

import com.ivan_naumenko.repository_analyzer.commitinsight.dto.CommitInsightResponse;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmCancelledException;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import com.ivan_naumenko.repository_analyzer.llm.coordinator.LlmRequestCoordinator;
import com.ivan_naumenko.repository_analyzer.report.config.ReportProperties;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import static com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.*;

/** Attach immutable source excerpts; optional model re-reading requires prior calibration. */
@Service
@RequiredArgsConstructor
public class GroundedReviewService {
    private final ReviewContextService contexts;
    private final GroundedReviewPrompt prompts;
    private final GroundedReviewParser parser;
    private final LlmRequestCoordinator requests;
    private final ReportProperties properties;
    private final tools.jackson.databind.ObjectMapper mapper;

    record Checked(CommitChangeSummary commit, ReviewContext context, GroundedReviewParser.Review review,
                   int rejected, String failure) {}

    public SynthesisResult refine(ReportGenerationData report, RepositoryFactsResponse facts,
                                  List<CommitChangeSummary> commits, List<CommitInsightResponse> insights,
                                  SynthesisResult draft) {
        if (!properties.groundedReviewEnabled()) return attachSources(report, facts, commits, draft);
        Map<Integer, CommitInsightResponse> initial = insights.stream().collect(Collectors.toMap(
                CommitInsightResponse::commitId, i -> i, (first, last) -> last));
        List<Checked> checked = new ArrayList<>();
        // Only this many contexts and requests can be in memory at once, regardless of repository size.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            int width = Math.max(1, properties.maxInFlight());
            for (int start = 0; start < commits.size(); start += width) {
                List<Future<Checked>> pending = new ArrayList<>();
                for (var commit : commits.subList(start, Math.min(start + width, commits.size())))
                    pending.add(executor.submit(() -> review(report, commit, initial.get(commit.commitId()))));
                try {
                    for (var future : pending) checked.add(future.get());
                } catch (InterruptedException interrupted) {
                    pending.forEach(future -> future.cancel(true));
                    Thread.currentThread().interrupt();
                    throw new CancellationException("Проверка отчёта остановлена");
                } catch (ExecutionException failed) {
                    pending.forEach(future -> future.cancel(true));
                    if (failed.getCause() instanceof RuntimeException cause) throw cause;
                    throw new IllegalStateException("Проверка отчёта не завершена", failed.getCause());
                }
            }
        }
        return assemble(facts, checked, draft);
    }

    /** Preserve the established report unless the extra model stage has passed calibration. */
    private SynthesisResult attachSources(ReportGenerationData report, RepositoryFactsResponse facts,
                                          List<CommitChangeSummary> commits, SynthesisResult draft) {
        Map<String, ReviewContext> sources = new LinkedHashMap<>();
        Map<String, Evidence> evidence = new LinkedHashMap<>();
        draft.artifacts().evidence().forEach(item -> evidence.put(item.id(), item));
        Set<String> used = new HashSet<>();
        draft.artifacts().summary().forEach(item -> used.addAll(item.evidenceIds()));
        draft.artifacts().sections().forEach(section -> section.findings().forEach(item -> used.addAll(item.evidenceIds())));
        draft.artifacts().actions().forEach(item -> used.addAll(item.evidenceIds()));
        Set<String> limitations = new LinkedHashSet<>(draft.artifacts().quality().warnings());
        int changed = 0, inspected = 0, limited = 0, contextFiles = 0;
        for (var commit : commits) {
            cancelled(); changed += commit.files().size();
            if (!used.contains("C" + commit.commitId()) || commit.merge()) { limited += commit.files().size(); continue; }
            try {
                var context = contexts.load(report, commit.commitId()); sources.put("C" + commit.commitId(), context);
                inspected += context.inspectedFiles(); limited += context.limitedFiles(); contextFiles += context.contextFiles();
                limitations.addAll(context.limitations());
            } catch (LlmCancelledException | CancellationException cancelled) { throw cancelled; }
            catch (RuntimeException unavailable) { limited += commit.files().size(); limitations.add("Для части ссылок не удалось загрузить фрагменты исходников."); }
        }
        List<Finding> summary = new ArrayList<>();
        String measured = "Коммитов: " + facts.totalCommits() + "; авторов: " + facts.authorCount()
                + "; изменённых файлов: " + facts.touchedFiles() + "; строк: +" + facts.addedLines() + " / −" + facts.deletedLines() + ".";
        summary.add(new Finding(measured, List.of("F1"), new Assessment("FACT", measured, "", "", "", List.of(), List.of())));
        draft.artifacts().summary().forEach(item -> summary.add(attach(item, sources, evidence)));
        List<Section> sections = draft.artifacts().sections().stream().filter(section -> !section.findings().isEmpty())
                .map(section -> new Section(section.title(), section.findings().stream().distinct()
                        .map(item -> attach(item, sources, evidence)).toList())).toList();
        limitations.add("Привязка источников проверена. Независимая смысловая проверка не выполнялась; выводы и уровни риска остаются оценками модели.");
        limitations.add("Фрагмент источника показывает доступный код, но сам по себе не доказывает правильность каждого вывода. Тесты проекта не запускались.");
        var coverage = new Coverage(commits.size(), 0, commits.size(), changed, inspected, limited, contextFiles,
                0, List.copyOf(limitations), Map.of(), "SOURCES_ONLY");
        var quality = new Quality("SOURCE_LINKED", draft.artifacts().quality().validatedFindings(), draft.artifacts().actions().size(),
                union(draft.artifacts().quality().checks(), List.of("Числовая сводка сформирована из рассчитанных показателей", "ID и пути источников проверены",
                        "Цитаты сохранены вместе с путём и версией Git")), List.copyOf(limitations));
        var artifacts = new com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts(List.copyOf(summary), sections,
                draft.artifacts().actions(), List.copyOf(evidence.values()), quality, coverage);
        return new SynthesisResult(markdown(artifacts), artifacts);
    }

    private Finding attach(Finding finding, Map<String, ReviewContext> sources, Map<String, Evidence> evidence) {
        List<Citation> citations = new ArrayList<>();
        for (String id : finding.evidenceIds()) {
            var context = sources.get(id);
            if (context == null) continue;
            citations.addAll(context.citations());
            for (var citation : context.citations()) if (!citation.side().equals("DIFF"))
                evidence.putIfAbsent(citation.evidenceId(), new Evidence(citation.evidenceId(), "CODE", context.commitId(),
                        citation.commitHash(), citation.path() + ":" + citation.startLine() + "–" + citation.endLine(),
                        List.of(citation.path()), "Фрагмент " + citation.side() + " из закреплённой версии Git"));
        }
        List<String> ids = union(finding.evidenceIds(), citations.stream().map(Citation::evidenceId).toList());
        return new Finding(finding.text(), ids, new Assessment("INFERENCE", finding.text(), "", "", "", List.of(), List.copyOf(citations)));
    }

    private Checked review(ReportGenerationData report, CommitChangeSummary commit, CommitInsightResponse previous) {
        if (commit.merge()) return new Checked(commit, null, null, 0, "Merge-коммит: отдельный разбор разрешения конфликтов не выполнен.");
        ReviewContext context = null;
        int rejected = 0;
        try {
            cancelled();
            context = contexts.load(report, commit.commitId());
            if (context.citations().stream().noneMatch(c -> c.side().equals("DIFF")))
                return new Checked(commit, context, null, 0, "Нет доступного текстового diff для повторной проверки.");
            String original = prompts.build(context, previous), prompt = original;
            for (int attempt = 0; attempt < 2; attempt++) {
                cancelled();
                String raw = requests.getOrGenerate(prompt, null, LlmRequestProfile.GROUNDED_REVIEW);
                try {
                    var review = parser.parse(raw, context, previous == null ? "UNKNOWN" : previous.riskLevel().name());
                    if (!review.items().isEmpty()) {
                        cancelled();
                        String checkPrompt = prompts.verify(context, raw);
                        String checkRaw = requests.getOrGenerate(checkPrompt, null, LlmRequestProfile.GROUNDED_VERIFY);
                        GroundedReviewParser.Verdict verdict;
                        try { verdict = parser.verdict(checkRaw); }
                        catch (IllegalStateException malformed) {
                            requests.discardResponse(checkPrompt, LlmRequestProfile.GROUNDED_VERIFY, checkRaw);
                            throw malformed;
                        }
                        if (!verdict.supported()) throw new IllegalStateException("Смысловая проверка: " + String.join("; ", verdict.issues()));
                    }
                    return new Checked(commit, context, review, rejected, null);
                } catch (IllegalStateException invalid) {
                    rejected++;
                    requests.discardResponse(prompt, LlmRequestProfile.GROUNDED_REVIEW, raw);
                    prompt = original + "\nVALIDATION_FEEDBACK: " + invalid.getMessage()
                            + "\nИсправь нарушение. Если данных недостаточно, верни пустой findings и объяснение в unknowns.";
                }
            }
            return new Checked(commit, context, null, rejected, "Ответ повторного разбора не прошёл проверку источников, риска или соответствия коду.");
        } catch (LlmCancelledException | CancellationException cancelled) {
            throw cancelled;
        } catch (RuntimeException unavailable) {
            cancelled();
            // Never substitute an old, unverified model claim when code or inference is unavailable.
            return new Checked(commit, context, null, rejected, "Повторная проверка недоступна; смысловые выводы по коммиту не включены.");
        }
    }

    SynthesisResult assemble(RepositoryFactsResponse facts, List<Checked> checked, SynthesisResult draft) {
        Map<String, Evidence> evidence = new LinkedHashMap<>();
        draft.artifacts().evidence().forEach(source -> evidence.put(source.id(), source));
        List<Finding> findings = new ArrayList<>(), unknowns = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        Map<Integer, String> risks = new LinkedHashMap<>();
        Set<String> limits = new LinkedHashSet<>();
        int reviewed = 0, changed = 0, inspected = 0, limited = 0, contextFiles = 0, rejected = 0;
        for (Checked result : checked) {
            var commit = result.commit();
            String id = "C" + commit.commitId();
            evidence.putIfAbsent(id, new Evidence(id, "COMMIT", commit.commitId(), commit.commitHash(),
                    commit.message() == null ? "Коммит" : commit.message().lines().findFirst().orElse("Коммит"),
                    commit.files().stream().map(file -> file.path()).distinct().toList(), "Коммит " + commit.commitHash()));
            changed += commit.files().size(); rejected += result.rejected();
            if (result.context() != null) {
                inspected += result.context().inspectedFiles(); limited += result.context().limitedFiles();
                contextFiles += result.context().contextFiles(); limits.addAll(result.context().limitations());
            } else limited += commit.files().size();
            var review = result.review();
            if (review == null || review.items().isEmpty()) {
                String reason = result.failure() != null ? result.failure() : String.join(" ", review.unknowns());
                unknowns.add(new Finding(reason, List.of(id), new Assessment("UNKNOWN", reason, "", "UNKNOWN", "", List.of(), List.of())));
                risks.put(commit.commitId(), "UNKNOWN");
                continue;
            }
            reviewed++;
            String level = review.items().stream().map(GroundedReviewParser.Item::riskLevel).max(Comparator.comparingInt(GroundedReviewService::rank)).orElse("UNKNOWN");
            risks.put(commit.commitId(), level);
            for (var item : review.items()) {
                for (var citation : item.citations()) if (!citation.side().equals("DIFF"))
                    evidence.putIfAbsent(citation.evidenceId(), new Evidence(citation.evidenceId(), "CODE", commit.commitId(),
                            citation.commitHash(), citation.path() + ":" + citation.startLine() + "–" + citation.endLine(),
                            List.of(citation.path()), "Фрагмент " + citation.side() + " из закреплённой версии Git"));
                List<String> ids = item.citations().stream().map(Citation::evidenceId).distinct().toList();
                List<String> missing = new ArrayList<>(item.unknowns()); missing.addAll(review.unknowns());
                findings.add(new Finding(item.title(), ids, new Assessment("INFERENCE", item.title(), item.impact(),
                        item.riskLevel(), item.risk(), missing.stream().distinct().toList(), item.citations())));
                if (!item.action().isEmpty()) actions.add(new Action("A" + (actions.size() + 1), item.action(),
                        item.risk().isEmpty() ? item.impact() : item.risk(), item.verification(),
                        item.riskLevel().equals("NONE") ? "LOW" : item.riskLevel(), ids,
                        item.citations().stream().map(Citation::path).distinct().toList()));
            }
        }
        var unique = new LinkedHashMap<String, Finding>();
        for (var finding : findings) {
            var a = finding.assessment();
            String key = finding.text() + "\n" + a.impact() + "\n" + a.riskLevel() + "\n" + a.risk();
            unique.merge(key, finding, (first, next) -> {
                var prior = first.assessment();
                return new Finding(first.text(), union(first.evidenceIds(), next.evidenceIds()),
                        new Assessment(prior.kind(), prior.observation(), prior.impact(), prior.riskLevel(), prior.risk(),
                                union(prior.unknowns(), next.assessment().unknowns()), union(prior.citations(), next.assessment().citations())));
            });
        }
        findings = new ArrayList<>(unique.values());
        var uniqueActions = new LinkedHashMap<String, Action>();
        for (var action : actions) uniqueActions.merge(action.title() + "\n" + action.reason() + "\n" + action.verification() + "\n" + action.priority(),
                action, (first, next) -> new Action(first.id(), first.title(), first.reason(), first.verification(), first.priority(),
                        union(first.evidenceIds(), next.evidenceIds()), union(first.paths(), next.paths())));
        actions = new ArrayList<>(uniqueActions.values());
        findings.sort(Comparator.comparingInt((Finding f) -> rank(f.assessment().riskLevel())).reversed());
        actions.sort(Comparator.comparingInt((Action a) -> rank(a.priority())).reversed());
        String factText = "Коммитов: " + facts.totalCommits() + "; авторов: " + facts.authorCount()
                + "; изменённых файлов: " + facts.touchedFiles() + "; строк: +" + facts.addedLines() + " / −" + facts.deletedLines() + ".";
        var fact = new Finding(factText, List.of("F1"), new Assessment("FACT", factText, "", "", "", List.of(), List.of()));
        List<Finding> summary = new ArrayList<>(); summary.add(fact); summary.addAll(findings.stream().limit(3).toList());
        // Use only the role-specific grouping from the first synthesis, never its unsupported prose.
        List<Section> sections = new ArrayList<>();
        Map<String, List<Finding>> grouped = new LinkedHashMap<>();
        for (var section : draft.artifacts().sections()) grouped.put(section.title(), new ArrayList<>());
        if (grouped.isEmpty()) grouped.put("Изменения и последствия", new ArrayList<>());
        for (var finding : findings) {
            String target = draft.artifacts().sections().stream().filter(section -> section.findings().stream()
                    .anyMatch(old -> old.evidenceIds().stream().anyMatch(finding.evidenceIds()::contains)))
                    .map(Section::title).findFirst().orElse(grouped.keySet().stream().reduce((a, b) -> b).orElseThrow());
            grouped.get(target).add(finding);
        }
        grouped.forEach((title, values) -> { if (!values.isEmpty()) sections.add(new Section(title, List.copyOf(values))); });
        if (!unknowns.isEmpty()) sections.add(new Section("Не удалось установить", List.copyOf(unknowns)));
        limits.add("Числа рассчитаны программой; цитаты взяты из сохранённого diff и закреплённых версий Git. Последствия и риски — оценки модели, а не доказанные результаты выполнения.");
        Coverage coverage = new Coverage(checked.size(), reviewed, checked.size() - reviewed, changed, inspected, limited,
                contextFiles, rejected, List.copyOf(limits), Map.copyOf(risks));
        var factsSource = evidence.get("F1");
        if (factsSource != null) evidence.put("F1", new Evidence("F1", "FACTS", null, factsSource.commitHash(),
                factsSource.label(), factsSource.paths(), mapper.writeValueAsString(ReviewedFacts.apply(facts, coverage))));
        Quality quality = new Quality(reviewed == checked.size() && limited == 0 ? "SOURCE_CHECKED" : "PARTIAL",
                findings.size(), actions.size(), List.of("Числовая сводка сформирована из рассчитанных показателей",
                "Каждая оценка ссылается на изменённые строки", "Цитаты, файлы и версии выбраны из серверного каталога",
                "Уровень риска согласован с заявленным механизмом", "Отдельный запрос проверил смысл выводов по старому и новому коду",
                "Не прошедшие проверку ответы исключены"), List.copyOf(limits));
        var artifacts = new com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts(List.copyOf(summary),
                List.copyOf(sections), List.copyOf(actions), List.copyOf(evidence.values()), quality, coverage);
        return new SynthesisResult(markdown(artifacts), artifacts);
    }

    private static String markdown(com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts artifacts) {
        var text = new StringBuilder("## Главное\n\n");
        for (var finding : artifacts.summary()) text.append("- ").append(finding.assessment().kind().equals("FACT") ? "Факт: " : "Оценка: ")
                .append(finding.text()).append(links(finding)).append('\n');
        for (var section : artifacts.sections()) {
            text.append("\n## ").append(section.title()).append("\n\n");
            for (var finding : section.findings()) {
                var assessment = finding.assessment();
                text.append("- ").append(assessment.kind().equals("UNKNOWN") ? "Не установлено: " : "Оценка: ")
                        .append(finding.text()).append(links(finding)).append('\n');
                if (!assessment.impact().isEmpty()) text.append("  Последствие: ").append(assessment.impact()).append('\n');
                if (!assessment.risk().isEmpty()) text.append("  Риск ").append(assessment.riskLevel()).append(": ").append(assessment.risk()).append('\n');
                assessment.unknowns().forEach(value -> text.append("  Не установлено: ").append(value).append('\n'));
            }
        }
        var c = artifacts.coverage();
        text.append("\n## Покрытие проверки\n\nНезависимая смысловая проверка: ").append(c.reviewedCommits()).append('/').append(c.totalCommits())
                .append(". Рассмотрены diff файлов: ").append(c.inspectedFiles()).append('/').append(c.changedFiles())
                .append(" (файл в разных коммитах учитывается отдельно). Ограничены или пропущены: ").append(c.limitedFiles()).append(".\n\n");
        c.limitations().forEach(value -> text.append("- ").append(value).append('\n'));
        return text.toString();
    }
    private static String links(Finding finding) {
        return finding.evidenceIds().stream().map(id -> " [" + id + "](#evidence-" + id + ")").collect(Collectors.joining());
    }
    private static <T> List<T> union(List<T> first, List<T> next) {
        var result = new LinkedHashSet<>(first); result.addAll(next); return List.copyOf(result);
    }
    static int rank(String risk) { return switch (risk) { case "HIGH" -> 4; case "MEDIUM" -> 3; case "LOW" -> 2; case "NONE" -> 1; default -> 0; }; }
    private static void cancelled() { if (Thread.currentThread().isInterrupted()) throw new CancellationException("Проверка отчёта остановлена"); }
}
