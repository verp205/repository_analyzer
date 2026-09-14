package com.ivan_naumenko.repository_analyzer.insight.cluster.service;

import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileRow;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.diff.service.TaskChangeService;
import com.ivan_naumenko.repository_analyzer.insight.cluster.dto.ChangeCluster;
import com.ivan_naumenko.repository_analyzer.insight.cluster.dto.ClusteredCommit;
import com.ivan_naumenko.repository_analyzer.insight.cluster.dto.CommitClustersResponse;
import com.ivan_naumenko.repository_analyzer.insight.cluster.model.ChangeClusterType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Группирует коммиты по основному типу работы. Тип выбирается по сумме
 * баллов: каждый файл голосует за свой тип, ключевые слова сообщения
 * (русские и английские, по целым словам) добавляют вес.
 */
@Service
@RequiredArgsConstructor
public class CommitClusteringService {

    private static final int FILE_WEIGHT = 2;
    private static final int SECURITY_FILE_WEIGHT = 5;
    private static final int MESSAGE_WEIGHT = 4;

    private static final Set<String> SECURITY_TOKENS = Set.of(
            "security", "auth", "authentication", "authorization", "oauth",
            "oauth2", "jwt", "csrf", "crypto", "credential", "credentials",
            "password", "passwords", "permission", "permissions", "acl",
            "secret", "secrets"
    );
    private static final Set<String> BUILD_FILE_NAMES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
            "settings.gradle.kts", "package.json", "package-lock.json",
            "yarn.lock", "pnpm-lock.yaml", "dockerfile", "docker-compose.yml",
            "docker-compose.yaml", "jenkinsfile", "makefile", "mvnw",
            "mvnw.cmd", ".gitignore", ".gitattributes", ".editorconfig"
    );
    private static final Set<String> BUILD_EXTENSIONS = Set.of(
            "properties", "yml", "yaml", "toml", "ini", "conf"
    );
    private static final Set<String> DOC_EXTENSIONS = Set.of(
            "md", "adoc", "rst", "txt"
    );

    private static final Map<ChangeClusterType, Keywords> MESSAGE_KEYWORDS = Map.of(
            ChangeClusterType.SECURITY, new Keywords(
                    Set.of("security", "auth", "authentication", "authorization",
                            "oauth", "jwt", "csrf", "xss", "cve", "vulnerability",
                            "secret", "secrets", "password", "permission", "permissions"),
                    Set.of("безопасн", "авторизац", "аутентификац", "уязвим",
                            "парол", "шифрова", "секрет")
            ),
            ChangeClusterType.TESTS, new Keywords(
                    Set.of("test", "tests", "testing", "junit", "mockito", "coverage"),
                    Set.of("тест")
            ),
            ChangeClusterType.DOCUMENTATION, new Keywords(
                    Set.of("readme", "docs", "doc", "documentation", "javadoc", "changelog"),
                    Set.of("документ", "readme", "описани")
            ),
            ChangeClusterType.BUILD_AND_CONFIGURATION, new Keywords(
                    Set.of("build", "ci", "deps", "dependency", "dependencies", "bump",
                            "docker", "maven", "gradle", "pom", "config",
                            "configuration", "pipeline"),
                    Set.of("сборк", "зависимост", "конфигурац", "настройк")
            ),
            ChangeClusterType.DATA, new Keywords(
                    Set.of("migration", "migrations", "schema", "database", "db",
                            "sql", "flyway", "liquibase", "entity", "entities"),
                    Set.of("миграц", "схем", "базы", "базу", "база", "хранени", "сущност")
            ),
            ChangeClusterType.API, new Keywords(
                    Set.of("api", "endpoint", "endpoints", "controller", "rest",
                            "graphql", "grpc", "swagger", "openapi"),
                    Set.of("эндпоинт", "контроллер", "интеграц")
            )
    );

    private final AnalysisTaskService taskService;
    private final TaskChangeService changeService;

    @Transactional(readOnly = true)
    public CommitClustersResponse cluster(Integer taskId) {
        taskService.getById(taskId);
        Map<ChangeClusterType, List<ClusteredCommit>> grouped =
                new EnumMap<>(ChangeClusterType.class);

        for (CommitChangeSummary commit : changeService.load(taskId)) {
            if (commit.merge()) {
                continue;
            }
            ChangeClusterType type = classify(
                    commit.message(),
                    commit.files().stream().map(ChangedFileRow::path).toList()
            );
            grouped.computeIfAbsent(type, ignored -> new ArrayList<>())
                    .add(new ClusteredCommit(
                            commit.commitId(),
                            commit.commitHash(),
                            commit.message(),
                            commit.files().size(),
                            commit.addedLines(),
                            commit.deletedLines()
                    ));
        }

        List<ChangeCluster> clusters = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ChangeCluster(
                        entry.getKey(),
                        entry.getKey().displayName(),
                        List.copyOf(entry.getValue())
                ))
                .toList();
        int total = clusters.stream()
                .mapToInt(cluster -> cluster.commits().size())
                .sum();

        return new CommitClustersResponse(taskId, total, clusters);
    }

    static ChangeClusterType classify(String message, List<String> paths) {
        Map<ChangeClusterType, Integer> scores = new EnumMap<>(ChangeClusterType.class);

        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            ChangeClusterType type = classifyPath(path);
            scores.merge(
                    type,
                    type == ChangeClusterType.SECURITY ? SECURITY_FILE_WEIGHT : FILE_WEIGHT,
                    Integer::sum
            );
        }

        Set<String> words = words(message);
        MESSAGE_KEYWORDS.forEach((type, keywords) -> {
            if (words.stream().anyMatch(keywords::matches)) {
                scores.merge(type, MESSAGE_WEIGHT, Integer::sum);
            }
        });

        return scores.entrySet().stream()
                .max(Comparator
                        .comparingInt((Map.Entry<ChangeClusterType, Integer> entry) -> entry.getValue())
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .orElse(ChangeClusterType.CORE);
    }

    static ChangeClusterType classifyPath(String rawPath) {
        String path = rawPath.toLowerCase(Locale.ROOT);
        String name = path.substring(path.lastIndexOf('/') + 1);
        String rawName = rawPath.substring(rawPath.lastIndexOf('/') + 1);
        String extension = name.contains(".")
                ? name.substring(name.lastIndexOf('.') + 1)
                : "";
        Set<String> pathTokens = tokens(rawPath);
        Set<String> nameTokens = tokens(rawName);
        String wrapped = "/" + path;

        if (pathTokens.stream().anyMatch(SECURITY_TOKENS::contains)) {
            return ChangeClusterType.SECURITY;
        }
        if (wrapped.contains("/test/") || wrapped.contains("/tests/")
                || wrapped.contains("/__tests__/")
                || nameTokens.contains("test") || nameTokens.contains("tests")
                || nameTokens.contains("spec")) {
            return ChangeClusterType.TESTS;
        }
        if (DOC_EXTENSIONS.contains(extension) || wrapped.contains("/docs/")
                || name.startsWith("license") || name.startsWith("changelog")) {
            return ChangeClusterType.DOCUMENTATION;
        }
        if (BUILD_FILE_NAMES.contains(name) || BUILD_EXTENSIONS.contains(extension)
                || path.startsWith(".github/") || path.startsWith(".mvn/")
                || name.startsWith("tsconfig") || name.contains("vite.config")
                || name.contains("eslint")) {
            return ChangeClusterType.BUILD_AND_CONFIGURATION;
        }
        if (extension.equals("sql") || wrapped.contains("/db/")
                || wrapped.contains("/migration/") || wrapped.contains("/migrations/")
                || wrapped.contains("/entity/") || wrapped.contains("/entities/")
                || nameTokens.contains("entity") || nameTokens.contains("repository")
                || nameTokens.contains("dao") || nameTokens.contains("migration")) {
            return ChangeClusterType.DATA;
        }
        if (wrapped.contains("/api/") || wrapped.contains("/controller/")
                || wrapped.contains("/dto/") || nameTokens.contains("controller")
                || nameTokens.contains("dto") || nameTokens.contains("endpoint")
                || extension.equals("proto") || extension.equals("graphql")
                || name.contains("openapi") || name.contains("swagger")) {
            return ChangeClusterType.API;
        }
        return ChangeClusterType.CORE;
    }

    private static Set<String> tokens(String value) {
        return Arrays.stream(value.split("(?<=[a-z0-9])(?=[A-Z])|[^A-Za-z0-9]+"))
                .map(token -> token.toLowerCase(Locale.ROOT))
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toSet());
    }

    private static Set<String> words(String message) {
        if (message == null || message.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(message.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toSet());
    }

    private record Keywords(Set<String> exact, Set<String> stems) {

        boolean matches(String word) {
            return exact.contains(word)
                    || stems.stream().anyMatch(word::startsWith);
        }
    }
}
