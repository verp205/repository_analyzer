package com.ivan_naumenko.repository_analyzer.commitinsight.service;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDraft;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.InsightWorkUnit;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.forge.model.PullRequestContext;
import com.ivan_naumenko.repository_analyzer.forge.service.ForgeContextService;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactCommitContext;
import com.ivan_naumenko.repository_analyzer.llm.context.service.StoredCommitContextService;
import com.ivan_naumenko.repository_analyzer.llm.coordinator.LlmRequestCoordinator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Выполняет единицу работы. Ошибка одного коммита не прерывает отчёт:
 * пакет при неудаче разбирается поштучно, а недоступность LLM
 * фиксируется как FAILED и повторяется при следующем запуске.
 * Остановка отчёта (interrupt потока) прерывает работу сразу.
 */
@Service
@RequiredArgsConstructor
public class CommitInsightGenerator {

    private static final Logger log = LoggerFactory.getLogger(
            CommitInsightGenerator.class
    );

    private final StoredCommitContextService contextService;
    private final CommitInsightPromptFactory promptFactory;
    private final LlmRequestCoordinator requestCoordinator;
    private final CommitInsightParser parser;
    private final ForgeContextService forgeContextService;

    public Map<Integer, CommitInsightDraft> generate(
            InsightWorkUnit unit,
            String repositoryUrl
    ) {
        Objects.requireNonNull(unit, "unit не должен быть null");
        return switch (unit.kind()) {
            case TEMPLATE -> templates(unit.commits(), repositoryUrl);
            case SINGLE -> Map.of(
                    unit.commits().getFirst().commitId(),
                    analyzeSingle(unit.commits().getFirst(), repositoryUrl)
            );
            case BATCH -> analyzeBatch(unit.commits(), repositoryUrl);
        };
    }

    private Map<Integer, CommitInsightDraft> templates(
            List<CommitChangeSummary> commits,
            String repositoryUrl
    ) {
        Map<Integer, CommitInsightDraft> result = new LinkedHashMap<>();
        for (CommitChangeSummary commit : commits) {
            result.put(
                    commit.commitId(),
                    CommitInsightTemplates.templateFor(commit)
                            .orElseGet(() -> analyzeSingle(commit, repositoryUrl))
            );
        }
        return result;
    }

    private CommitInsightDraft analyzeSingle(CommitChangeSummary commit, String repositoryUrl) {
        ensureNotCancelled();
        try {
            CompactCommitContext context = contextService.load(commit.commitId());
            PullRequestContext pullRequest = pullRequest(commit, repositoryUrl);
            String response = requestCoordinator.getOrGenerate(
                    promptFactory.buildSingle(context, pullRequest),
                    null,
                    LlmRequestProfile.COMMIT_ANALYSIS
            );
            CommitInsightDraft draft = parser.parseSingle(response).orElseGet(() ->
                    CommitInsightTemplates.unstructured(parser.fallbackSummary(response))
            );
            return attach(draft, pullRequest);
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            ensureNotCancelled();
            log.warn(
                    "Не удалось проанализировать коммит {}",
                    commit.commitHash(),
                    exception
            );
            return CommitInsightTemplates.failed(exception);
        }
    }

    private Map<Integer, CommitInsightDraft> analyzeBatch(
            List<CommitChangeSummary> commits,
            String repositoryUrl
    ) {
        ensureNotCancelled();
        Map<Integer, PullRequestContext> pullRequests = new HashMap<>();
        Map<String, CommitInsightDraft> parsed;
        try {
            var loaded = contextService.loadAll(commits.stream().map(CommitChangeSummary::commitId).toList());
            List<CompactCommitContext> contexts = commits.stream()
                    .map(commit -> loaded.get(commit.commitId()))
                    .toList();
            Map<String, PullRequestContext> byHash = new LinkedHashMap<>();
            for (CommitChangeSummary commit : commits) {
                PullRequestContext pullRequest = pullRequest(commit, repositoryUrl);
                if (pullRequest != null) {
                    pullRequests.put(commit.commitId(), pullRequest);
                    byHash.put(commit.commitHash(), pullRequest);
                }
            }
            String response = requestCoordinator.getOrGenerate(
                    promptFactory.buildBatch(contexts, byHash),
                    null,
                    LlmRequestProfile.COMMIT_BATCH
            );
            parsed = parser.parseBatch(response);
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            ensureNotCancelled();
            log.warn(
                    "Пакетный анализ {} коммитов не удался, выполняется поштучно",
                    commits.size(),
                    exception
            );
            parsed = Map.of();
        }

        Map<Integer, CommitInsightDraft> result = new LinkedHashMap<>();
        for (CommitChangeSummary commit : commits) {
            CommitInsightDraft draft = parsed.get(shortHash(commit));
            result.put(
                    commit.commitId(),
                    draft != null
                            ? attach(draft, pullRequests.get(commit.commitId()))
                            : analyzeSingle(commit, repositoryUrl)
            );
        }
        return result;
    }

    private PullRequestContext pullRequest(CommitChangeSummary commit, String repositoryUrl) {
        return forgeContextService
                .findForCommit(repositoryUrl, commit.commitId(), commit.commitHash())
                .orElse(null);
    }

    private CommitInsightDraft attach(CommitInsightDraft draft, PullRequestContext pullRequest) {
        if (pullRequest == null) {
            return draft;
        }
        return new CommitInsightDraft(
                draft.status(),
                draft.category(),
                draft.riskLevel(),
                draft.summary(),
                draft.details().withPullRequest(pullRequest.toRef())
        );
    }

    private void ensureNotCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Анализ коммитов остановлен");
        }
    }

    private String shortHash(CommitChangeSummary commit) {
        String hash = commit.commitHash().toLowerCase(Locale.ROOT);
        return hash.length() > CommitInsightParser.SHORT_HASH_LENGTH
                ? hash.substring(0, CommitInsightParser.SHORT_HASH_LENGTH)
                : hash;
    }
}
