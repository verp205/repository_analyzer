package com.ivan_naumenko.repository_analyzer.forge.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;

@Validated
@ConfigurationProperties(prefix = "repository-analyzer.forge")
public record ForgeProperties(
        @DefaultValue("true")
        boolean enabled,

        /* Без токена GitHub разрешает около 60 запросов в час. */
        @DefaultValue("")
        String githubToken,

        @DefaultValue("https://api.github.com")
        String githubApiUrl,

        @DefaultValue("")
        String gitlabToken,

        /* Хосты self-hosted GitLab, кроме gitlab.com и gitlab.*. */
        @DefaultValue
        List<String> gitlabHosts,

        @DefaultValue("10000")
        @Min(500)
        long requestTimeoutMillis,

        @DefaultValue("8")
        @Min(0)
        @Max(50)
        int maxComments,

        @DefaultValue("3")
        @Min(0)
        @Max(10)
        int maxIssues,

        @DefaultValue("1500")
        @Min(200)
        @Max(20_000)
        int maxBodyChars
) {

    public ForgeProperties {
        githubToken = githubToken == null ? "" : githubToken.strip();
        gitlabToken = gitlabToken == null ? "" : gitlabToken.strip();
        githubApiUrl = githubApiUrl == null || githubApiUrl.isBlank()
                ? "https://api.github.com"
                : githubApiUrl.strip().replaceAll("/+$", "");
        gitlabHosts = gitlabHosts == null
                ? List.of()
                : gitlabHosts.stream()
                .map(host -> host.strip().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .toList();
    }
}
