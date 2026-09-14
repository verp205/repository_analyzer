package com.ivan_naumenko.repository_analyzer.forge.model;

/**
 * Репозиторий на GitHub или GitLab.
 *
 * @param apiBaseUrl  корень REST API без завершающего слеша
 * @param projectPath owner/repo для GitHub или полный путь проекта для GitLab
 */
public record ForgeRepository(
        ForgeProvider provider,
        String apiBaseUrl,
        String projectPath
) {

    public String cacheKey() {
        return provider + ":" + apiBaseUrl + ":" + projectPath;
    }
}
