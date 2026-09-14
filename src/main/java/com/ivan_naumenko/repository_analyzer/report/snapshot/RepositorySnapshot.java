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
        boolean truncated
) {

    public RepositorySnapshot {
        stack = stack == null ? List.of() : List.copyOf(stack);
        modules = modules == null ? List.of() : List.copyOf(modules);
        controllers = controllers == null ? List.of() : List.copyOf(controllers);
        security = security == null ? SecurityFacts.empty() : security;
    }

    public static RepositorySnapshot unavailable() {
        return new RepositorySnapshot(false, null, List.of(), List.of(), 0, 0, List.of(), SecurityFacts.empty(), false);
    }

    public record ModuleFact(String name, int files) {
    }

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
