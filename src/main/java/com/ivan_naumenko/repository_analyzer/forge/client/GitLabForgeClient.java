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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ivan_naumenko.repository_analyzer.forge.client.ForgeTexts.compact;
import static com.ivan_naumenko.repository_analyzer.forge.client.ForgeTexts.number;
import static com.ivan_naumenko.repository_analyzer.forge.client.ForgeTexts.text;

@Component
@RequiredArgsConstructor
public class GitLabForgeClient implements ForgeClient {

    private static final int COMMENT_LENGTH = 300;
    private static final int ISSUE_BODY_LENGTH = 500;

    private final ForgeHttpClient http;
    private final ForgeProperties properties;

    @Override
    public ForgeProvider provider() {
        return ForgeProvider.GITLAB;
    }

    @Override
    public Optional<PullRequestSummary> findPullRequestForCommit(
            ForgeRepository repository,
            String commitHash
    ) {
        Optional<JsonNode> response = http.getJson(
                uri(repository, "/repository/commits/" + commitHash + "/merge_requests"),
                headers()
        );
        if (response.isEmpty() || !response.get().isArray() || response.get().isEmpty()) {
            return Optional.empty();
        }

        JsonNode requests = response.get();
        JsonNode selected = requests.path(0);
        for (int index = 0; index < requests.size(); index++) {
            if ("merged".equals(text(requests.path(index).path("state")))) {
                selected = requests.path(index);
                break;
            }
        }
        return Optional.of(new PullRequestSummary(
                number(selected.path("iid")),
                text(selected.path("title")),
                text(selected.path("description")),
                text(selected.path("web_url"))
        ));
    }

    @Override
    public PullRequestContext loadDetails(ForgeRepository repository, PullRequestSummary summary) {
        List<String> comments = new ArrayList<>();
        if (properties.maxComments() > 0) {
            http.getJson(
                    uri(repository, "/merge_requests/" + summary.number()
                            + "/notes?sort=asc&order_by=created_at&per_page="
                            + Math.min(100, properties.maxComments() * 3)),
                    headers()
            ).filter(JsonNode::isArray).ifPresent(notes -> {
                for (int index = 0; index < notes.size() && comments.size() < properties.maxComments(); index++) {
                    JsonNode note = notes.path(index);
                    if (note.path("system").asBoolean(false)) {
                        continue;
                    }
                    String body = compact(text(note.path("body")), COMMENT_LENGTH);
                    if (!body.isBlank()) {
                        comments.add(text(note.path("author").path("username")) + ": " + body);
                    }
                }
            });
        }

        List<IssueContext> issues = new ArrayList<>();
        if (properties.maxIssues() > 0) {
            http.getJson(
                    uri(repository, "/merge_requests/" + summary.number() + "/closes_issues"),
                    headers()
            ).filter(JsonNode::isArray).ifPresent(array -> {
                for (int index = 0; index < array.size() && issues.size() < properties.maxIssues(); index++) {
                    JsonNode issue = array.path(index);
                    issues.add(new IssueContext(
                            number(issue.path("iid")),
                            text(issue.path("title")),
                            text(issue.path("web_url")),
                            compact(text(issue.path("description")), ISSUE_BODY_LENGTH)
                    ));
                }
            });
        }

        return new PullRequestContext(
                ForgeProvider.GITLAB,
                summary.number(),
                summary.title(),
                summary.url(),
                compact(summary.body(), properties.maxBodyChars()),
                comments,
                issues
        );
    }

    private URI uri(ForgeRepository repository, String path) {
        String projectId = URLEncoder.encode(repository.projectPath(), StandardCharsets.UTF_8);
        return URI.create(repository.apiBaseUrl() + "/projects/" + projectId + path);
    }

    private Map<String, String> headers() {
        return properties.gitlabToken().isEmpty()
                ? Map.of()
                : Map.of("PRIVATE-TOKEN", properties.gitlabToken());
    }
}
