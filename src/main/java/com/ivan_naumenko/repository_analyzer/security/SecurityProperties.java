package com.ivan_naumenko.repository_analyzer.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;

@Validated
@ConfigurationProperties(prefix = "repository-analyzer.security")
public record SecurityProperties(
        /* Разрешить репозитории во внутренней сети (например, корпоративный GitLab). */
        @DefaultValue("false")
        boolean allowPrivateHosts,

        /* Если список не пуст, клонировать можно только с этих хостов и их поддоменов. */
        @DefaultValue
        List<String> allowedHosts,

        /* Если задан, каждый запрос к /api/** должен содержать этот токен. */
        @DefaultValue("")
        String apiToken
) {

    public SecurityProperties {
        allowedHosts = allowedHosts == null
                ? List.of()
                : allowedHosts.stream()
                .map(host -> host.strip().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .toList();
        apiToken = apiToken == null ? "" : apiToken.strip();
    }
}
