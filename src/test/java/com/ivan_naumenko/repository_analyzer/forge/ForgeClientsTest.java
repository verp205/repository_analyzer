package com.ivan_naumenko.repository_analyzer.forge;

import com.ivan_naumenko.repository_analyzer.forge.client.ForgeHttpClient;
import com.ivan_naumenko.repository_analyzer.forge.client.GitHubForgeClient;
import com.ivan_naumenko.repository_analyzer.forge.client.GitLabForgeClient;
import com.ivan_naumenko.repository_analyzer.forge.config.ForgeProperties;
import com.ivan_naumenko.repository_analyzer.forge.exception.ForgeRateLimitException;
import com.ivan_naumenko.repository_analyzer.forge.model.ForgeProvider;
import com.ivan_naumenko.repository_analyzer.forge.model.ForgeRepository;
import com.ivan_naumenko.repository_analyzer.forge.model.IssueContext;
import com.ivan_naumenko.repository_analyzer.forge.model.PullRequestContext;
import com.ivan_naumenko.repository_analyzer.forge.model.PullRequestSummary;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeClientsTest {

    private HttpServer server;
    private final Map<String, Route> routes = new ConcurrentHashMap<>();
    private final Map<String, String> lastHeaders = new ConcurrentHashMap<>();

    private record Route(int status, String body, Map<String, String> headers) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            exchange.getRequestHeaders().forEach((name, values) ->
                    lastHeaders.put(name.toLowerCase(), values.getFirst()));
            Route route = routes.getOrDefault(path, new Route(404, "{}", Map.of()));
            byte[] body = route.body().getBytes(StandardCharsets.UTF_8);
            route.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(route.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void githubClientCollectsPullRequestDiscussionAndClosingIssues() {
        route("/repos/o/r/commits/abc/pulls", 200, """
                [
                  {"number":5,"title":"Черновик","body":"x","html_url":"u5","merged_at":null},
                  {"number":12,"title":"Оплата картой","body":"<!-- шаблон -->Fixes #7, см. также #9","html_url":"https://github.com/o/r/pull/12","merged_at":"2026-01-01T00:00:00Z"}
                ]
                """);
        route("/repos/o/r/issues/12/comments", 200, """
                [{"user":{"login":"alice"},"body":"Проверьте таймауты"},{"user":{"login":"bob"},"body":"  "}]
                """);
        route("/repos/o/r/issues/7", 200, """
                {"title":"Нужна оплата картой","body":"Клиенты просят","html_url":"https://github.com/o/r/issues/7"}
                """);
        route("/repos/o/r/issues/9", 200, """
                {"title":"Другой PR","pull_request":{},"html_url":"https://github.com/o/r/pull/9"}
                """);
        route("/repos/o/r/commits/nothing/pulls", 200, "[]");

        GitHubForgeClient client = new GitHubForgeClient(http(), properties());
        ForgeRepository repository = new ForgeRepository(ForgeProvider.GITHUB, baseUrl(), "o/r");

        PullRequestSummary summary = client.findPullRequestForCommit(repository, "abc").orElseThrow();
        assertEquals(12, summary.number());
        assertEquals("Bearer gh-token", lastHeaders.get("authorization"));

        PullRequestContext context = client.loadDetails(repository, summary);
        assertEquals("Оплата картой", context.title());
        assertEquals("Fixes #7, см. также #9", context.body());
        assertEquals(List.of("alice: Проверьте таймауты"), context.comments());
        assertEquals(
                List.of(new IssueContext(7, "Нужна оплата картой", "https://github.com/o/r/issues/7", "Клиенты просят")),
                context.issues()
        );
        assertEquals(List.of("#7 Нужна оплата картой"), context.toRef().issueTitles());

        assertTrue(client.findPullRequestForCommit(repository, "nothing").isEmpty());
        assertTrue(client.findPullRequestForCommit(repository, "unknown").isEmpty());
    }

    @Test
    void githubRateLimitIsReportedWithResetTime() {
        route("/repos/o/r/commits/limited/pulls", 403, "{\"message\":\"API rate limit exceeded\"}",
                Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", "2000000000"));

        GitHubForgeClient client = new GitHubForgeClient(http(), properties());
        ForgeRateLimitException exception = assertThrows(
                ForgeRateLimitException.class,
                () -> client.findPullRequestForCommit(
                        new ForgeRepository(ForgeProvider.GITHUB, baseUrl(), "o/r"),
                        "limited"
                )
        );
        assertEquals(Instant.ofEpochSecond(2_000_000_000L), exception.retryAt());
    }

    @Test
    void gitlabClientCollectsMergeRequestNotesAndClosedIssues() {
        String project = "/api/v4/projects/group%2Fapp";
        route(project + "/repository/commits/abc/merge_requests", 200, """
                [{"iid":3,"title":"Кэш каталога","description":"Добавляет кэш","web_url":"https://gitlab.com/group/app/-/merge_requests/3","state":"merged"}]
                """);
        route(project + "/merge_requests/3/notes", 200, """
                [
                  {"body":"added 1 commit","system":true,"author":{"username":"bot"}},
                  {"body":"Нужна инвалидация при обновлении","system":false,"author":{"username":"bob"}}
                ]
                """);
        route(project + "/merge_requests/3/closes_issues", 200, """
                [{"iid":4,"title":"Каталог грузится медленно","description":"5 секунд на открытие","web_url":"https://gitlab.com/group/app/-/issues/4"}]
                """);

        GitLabForgeClient client = new GitLabForgeClient(http(), properties());
        ForgeRepository repository = new ForgeRepository(ForgeProvider.GITLAB, baseUrl() + "/api/v4", "group/app");

        PullRequestSummary summary = client.findPullRequestForCommit(repository, "abc").orElseThrow();
        assertEquals("gl-token", lastHeaders.get("private-token"));

        PullRequestContext context = client.loadDetails(repository, summary);
        assertEquals(3, context.number());
        assertEquals(List.of("bob: Нужна инвалидация при обновлении"), context.comments());
        assertEquals("Каталог грузится медленно", context.issues().getFirst().title());
    }

    private void route(String path, int status, String body) {
        route(path, status, body, Map.of());
    }

    private void route(String path, int status, String body, Map<String, String> headers) {
        routes.put(path, new Route(status, body, headers));
    }

    private ForgeHttpClient http() {
        return new ForgeHttpClient(properties(), JsonMapper.builder().build(), HttpClient.newHttpClient());
    }

    private ForgeProperties properties() {
        return new ForgeProperties(true, "gh-token", baseUrl(), "gl-token", List.of(), 5_000, 8, 3, 1_500);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
