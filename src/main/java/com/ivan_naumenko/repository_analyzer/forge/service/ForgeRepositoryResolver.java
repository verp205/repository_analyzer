package com.ivan_naumenko.repository_analyzer.forge.service;

import com.ivan_naumenko.repository_analyzer.forge.config.ForgeProperties;
import com.ivan_naumenko.repository_analyzer.forge.model.ForgeProvider;
import com.ivan_naumenko.repository_analyzer.forge.model.ForgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Определяет по URL репозитория, на каком хостинге он размещён. */
@Component
@RequiredArgsConstructor
public class ForgeRepositoryResolver {

    private static final Pattern REPOSITORY_URL = Pattern.compile(
            "^https?://(?:[^@/]+@)?([^/:]+)(?::\\d+)?/(.+?)(?:\\.git)?/*$",
            Pattern.CASE_INSENSITIVE
    );

    private final ForgeProperties properties;

    public Optional<ForgeRepository> resolve(String repositoryUrl) {
        if (repositoryUrl == null) {
            return Optional.empty();
        }
        Matcher matcher = REPOSITORY_URL.matcher(repositoryUrl.strip());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String host = matcher.group(1).toLowerCase(Locale.ROOT);
        String path = matcher.group(2);

        if (host.equals("github.com") || host.equals("www.github.com")) {
            String[] segments = path.split("/");
            if (segments.length < 2 || segments[0].isBlank() || segments[1].isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new ForgeRepository(
                    ForgeProvider.GITHUB,
                    properties.githubApiUrl(),
                    segments[0] + "/" + segments[1]
            ));
        }

        if (host.equals("gitlab.com")
                || host.startsWith("gitlab.")
                || properties.gitlabHosts().contains(host)) {
            if (!path.contains("/")) {
                return Optional.empty();
            }
            return Optional.of(new ForgeRepository(
                    ForgeProvider.GITLAB,
                    "https://" + host + "/api/v4",
                    path
            ));
        }
        return Optional.empty();
    }
}
