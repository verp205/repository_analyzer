package com.ivan_naumenko.repository_analyzer.report.snapshot;

import java.util.List;

/**
 * Состояние кода на последнем коммите ветки, собранное без LLM: описание,
 * стек, модули и признаки защиты. Даёт модели факты там, где по одной
 * истории изменений можно только гадать.
 */
public record RepositorySnapshot(
        boolean available,
        String description,
        List<String> stack,
        List<ModuleFact> modules,
        int mainSourceFiles,
        int testSourceFiles,
        List<String> controllers,
        SecurityFacts security,
        boolean truncated,
        List<String> sourcePaths,
        List<LanguageFact> languages,
        List<String> manifests,
        String analysisCoverage
) {

    public RepositorySnapshot(boolean available, String description, List<String> stack, List<ModuleFact> modules,
                              int mainSourceFiles, int testSourceFiles, List<String> controllers, SecurityFacts security,
                              boolean truncated, List<String> sourcePaths) {
        this(available, description, stack, modules, mainSourceFiles, testSourceFiles, controllers, security,
                truncated, sourcePaths, List.of(), List.of(), "Текстовые признаки; структурный разбор Java; признаки защиты Spring.");
    }

    public RepositorySnapshot(boolean available, String description, List<String> stack, List<ModuleFact> modules,
                              int mainSourceFiles, int testSourceFiles, List<String> controllers, SecurityFacts security,
                              boolean truncated) {
        this(available, description, stack, modules, mainSourceFiles, testSourceFiles, controllers, security, truncated, List.of());
    }

    public RepositorySnapshot {
        sourcePaths = sourcePaths == null ? List.of() : List.copyOf(sourcePaths);
        stack = stack == null ? List.of() : List.copyOf(stack);
        modules = modules == null ? List.of() : List.copyOf(modules);
        controllers = controllers == null ? List.of() : List.copyOf(controllers);
        security = security == null ? SecurityFacts.empty() : security;
        languages = languages == null ? List.of() : List.copyOf(languages);
        manifests = manifests == null ? List.of() : List.copyOf(manifests);
        analysisCoverage = analysisCoverage == null ? "Текстовый анализ; отсутствие признака не доказывает отсутствие риска." : analysisCoverage;
    }

    public static RepositorySnapshot unavailable() {
        return new RepositorySnapshot(false, null, List.of(), List.of(), 0, 0, List.of(), SecurityFacts.empty(), false);
    }

    public record ModuleFact(String name, int files) {
    }

    public record LanguageFact(String language, int files) {}

    /**
     * Признаки найдены по тексту файлов и не заменяют аудит: отсутствие
     * признака означает «не обнаружено», а не «отсутствует».
     */
    public record SecurityFacts(
            boolean securityDependency,
            boolean securityConfiguration,
            int methodSecurityAnnotations,
            boolean csrfDisabled,
            boolean corsWildcard,
            boolean actuatorExposesAll,
            List<String> secretsInConfig,
            List<String> committedEnvFiles
    ) {

        public SecurityFacts {
            secretsInConfig = secretsInConfig == null ? List.of() : List.copyOf(secretsInConfig);
            committedEnvFiles = committedEnvFiles == null ? List.of() : List.copyOf(committedEnvFiles);
        }

        public static SecurityFacts empty() {
            return new SecurityFacts(false, false, 0, false, false, false, List.of(), List.of());
        }
    }
}
