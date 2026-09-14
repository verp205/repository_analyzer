package com.ivan_naumenko.repository_analyzer.forge;

import com.ivan_naumenko.repository_analyzer.forge.config.ForgeProperties;
import com.ivan_naumenko.repository_analyzer.forge.model.ForgeProvider;
import com.ivan_naumenko.repository_analyzer.forge.model.ForgeRepository;
import com.ivan_naumenko.repository_analyzer.forge.service.ForgeRepositoryResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeRepositoryResolverTest {

    private final ForgeRepositoryResolver resolver = new ForgeRepositoryResolver(
            new ForgeProperties(true, "", "https://api.github.com/", "", List.of("git.company.ru"), 10_000, 8, 3, 1_500)
    );

    @Test
    void resolvesGithubRepositories() {
        assertEquals(
                new ForgeRepository(ForgeProvider.GITHUB, "https://api.github.com", "Owner/Repo"),
                resolver.resolve("https://github.com/Owner/Repo.git").orElseThrow()
        );
        assertEquals(
                "owner/repo",
                resolver.resolve("https://github.com/owner/repo/tree/main").orElseThrow().projectPath()
        );
    }

    @Test
    void resolvesGitlabIncludingNestedGroupsAndSelfHosted() {
        assertEquals(
                new ForgeRepository(ForgeProvider.GITLAB, "https://gitlab.com/api/v4", "group/sub/project"),
                resolver.resolve("https://gitlab.com/group/sub/project.git").orElseThrow()
        );
        assertEquals(
                new ForgeRepository(ForgeProvider.GITLAB, "https://git.company.ru/api/v4", "team/app"),
                resolver.resolve("https://git.company.ru/team/app").orElseThrow()
        );
        assertEquals(
                ForgeProvider.GITLAB,
                resolver.resolve("https://gitlab.example.org/team/app").orElseThrow().provider()
        );
    }

    @Test
    void ignoresUnsupportedOrIncompleteUrls() {
        assertTrue(resolver.resolve("https://bitbucket.org/owner/repo").isEmpty());
        assertTrue(resolver.resolve("https://gitlab.com/project").isEmpty());
        assertTrue(resolver.resolve("https://github.com/owner").isEmpty());
        assertTrue(resolver.resolve("not a url").isEmpty());
        assertTrue(resolver.resolve(null).isEmpty());
    }
}
