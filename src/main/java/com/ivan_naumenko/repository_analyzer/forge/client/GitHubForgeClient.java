package com.ivan_naumenko.repository_analyzer.forge.client;

import com.ivan_naumenko.repository_analyzer.forge.config.ForgeProperties;
import com.ivan_naumenko.repository_analyzer.forge.model.ForgeProvider;
import com.ivan_naumenko.repository_analyzer.forge.model.ForgeRepository;
import com.ivan_naumenko.repository_analyzer.forge.model.IssueContext;
import com.ivan_naumenko.repository_analyzer.forge.model.PullRequestContext;
import com.ivan_naumenko.repository_analyzer.forge.model.PullRequestSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ivan_naumenko.repository_analyzer.forge.client.ForgeTexts.compact;
import static com.ivan_naumenko.repository_analyzer.forge.client.ForgeTexts.number;
import static com.ivan_naumenko.repository_analyzer.forge.client.ForgeTexts.text;

@Component
@RequiredArgsConstructor
public class GitHubForgeClient implements ForgeClient {

    private static final int COMMENT_LENGTH = 300;
    private static final int ISSUE_BODY_LENGTH = 500;
    private static final Pattern CLOSING_REFERENCE = Pattern.compile(
            "(?i)\\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\\s*:?\\s+#(\\d+)"
    );
    private static final Pattern ANY_REFERENCE = Pattern.compile("(?<![\\w/])#(\\d+)\\b");

    private final ForgeHttpClient http;
    private final ForgeProperties properties;

    @Override
    public ForgeProvider provider() {
        return ForgeProvider.GITHUB;
    }

    @Override
    public Optional<PullRequestSummary> findPullRequestForCommit(
            ForgeRepository repository,
            String commitHash
    ) {
        Optional<JsonNode> response = http.getJson(
                uri(repository, "/commits/" + commitHash + "/pulls"),
                headers()
        );
        if (response.isEmpty() || !response.get().isArray() || response.get().isEmpty()) {
            return Optional.empty();
        }

        JsonNode pulls = response.get();
        JsonNode selected = pulls.path(0);
        for (int index = 0; index < pulls.size(); index++) {
            if (pulls.path(index).path("merged_at").isString()) {
                selected = pulls.path(index);
                break;
            }
        }
        return Optional.of(new PullRequestSummary(
                number(selected.path("number")),
                text(selected.path("title")),
                text(selected.path("body")),
                text(selected.path("html_url"))
        ));
    }

    @Override
    public PullRequestContext loadDetails(ForgeRepository repository, PullRequestSummary summary) {
        List<String> comments = new ArrayList<>();
        if (properties.maxComments() > 0) {
            http.getJson(
                    uri(repository, "/issues/" + summary.number()
                            + "/comments?per_page=" + properties.maxComments()),
                    headers()
            ).filter(JsonNode::isArray).ifPresent(array -> {
                for (int index = 0; index < array.size() && comments.size() < properties.maxComments(); index++) {
                    JsonNode comment = array.path(index);
                    String body = compact(text(comment.path("body")), COMMENT_LENGTH);
                    if (!body.isBlank()) {
                        comments.add(text(comment.path("user").path("login")) + ": " + body);
                    }
                }
            });
        }

        List<IssueContext> issues = new ArrayList<>();
        for (int issueNumber : referencedIssues(summary)) {
            if (issues.size() >= properties.maxIssues()) {
                break;
            }
            http.getJson(uri(repository, "/issues/" + issueNumber), headers())
                    .filter(issue -> !issue.has("pull_request"))
                    .ifPresent(issue -> issues.add(new IssueContext(
                            issueNumber,
                            text(issue.path("title")),
                            text(issue.path("html_url")),
                            compact(text(issue.path("body")), ISSUE_BODY_LENGTH)
                    )));
        }

        return new PullRequestContext(
                ForgeProvider.GITHUB,
                summary.number(),
                summary.title(),
                summary.url(),
                compact(summary.body(), properties.maxBodyChars()),
                comments,
                issues
        );
    }

    /** Сначала задачи с ключевыми словами закрытия, затем прочие упоминания #N. */
    private Set<Integer> referencedIssues(PullRequestSummary summary) {
        Set<Integer> result = new LinkedHashSet<>();
        String text = summary.title() + "\n" + summary.body();
        collect(CLOSING_REFERENCE.matcher(text), result);
        collect(ANY_REFERENCE.matcher(text), result);
        result.remove(summary.number());
        return result;
    }

    private void collect(Matcher matcher, Set<Integer> target) {
        while (matcher.find()) {
            try {
                target.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Слишком длинный номер — не задача.
            }
        }
    }

    private URI uri(ForgeRepository repository, String path) {
        return URI.create(repository.apiBaseUrl() + "/repos/" + repository.projectPath() + path);
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/vnd.github+json");
        headers.put("X-GitHub-Api-Version", "2022-11-28");
        if (!properties.githubToken().isEmpty()) {
            headers.put("Authorization", "Bearer " + properties.githubToken());
        }
        return headers;
    }
}
