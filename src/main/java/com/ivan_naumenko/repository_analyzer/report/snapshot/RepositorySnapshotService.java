package com.ivan_naumenko.repository_analyzer.report.snapshot;

import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import com.ivan_naumenko.repository_analyzer.report.snapshot.RepositorySnapshot.ModuleFact;
import com.ivan_naumenko.repository_analyzer.report.snapshot.RepositorySnapshot.SecurityFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Читает рабочую копию ветки (после анализа она совпадает с последним
 * коммитом) и собирает {@link RepositorySnapshot}. Работает по тексту файлов,
 * без компиляции, с жёсткими лимитами, чтобы не тормозить на больших проектах.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepositorySnapshotService {

    private static final int MAX_VISITED_FILES = 30_000;
    private static final int MAX_READ_FILES = 4_000;
    private static final long MAX_FILE_BYTES = 256 * 1024;
    private static final int MAX_DESCRIPTION_LENGTH = 400;
    private static final int MAX_MODULES = 8;
    private static final int MAX_LISTED = 10;

    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", ".idea", ".gradle", ".mvn", ".next", "node_modules", "target",
            "build", "dist", "out", "vendor", "coverage"
    );
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            "java", "kt", "scala", "groovy", "ts", "tsx", "js", "jsx", "py", "go",
            "cs", "rb", "php", "rs", "swift", "cpp", "c"
    );
    private static final Pattern README = Pattern.compile("(?i)readme(\\.(md|rst|txt|adoc))?");
    private static final Pattern TEST_PATH = Pattern.compile(
            "(^|/)(?i:src/test|test|tests|__tests__|spec)/|(Test|Tests|IT)\\.[a-z]+$|\\.(test|spec)\\.[a-z]+$"
    );
    private static final Pattern CONTROLLER = Pattern.compile("@(Rest)?Controller\\b");
    private static final Pattern SECURITY_CONFIGURATION = Pattern.compile(
            "SecurityFilterChain|@EnableWebSecurity|WebSecurityConfigurerAdapter|@EnableMethodSecurity"
                    + "|@EnableGlobalMethodSecurity|SecurityWebFilterChain"
    );
    private static final Pattern METHOD_SECURITY = Pattern.compile("@(PreAuthorize|PostAuthorize|Secured|RolesAllowed)\\b");
    private static final Pattern CSRF_DISABLED = Pattern.compile(
            "csrf\\s*\\(\\s*\\)\\s*\\.\\s*disable\\s*\\(|csrf\\s*\\(\\s*AbstractHttpConfigurer::disable"
                    + "|csrf\\s*\\(\\s*\\w+\\s*->\\s*\\w+\\s*\\.\\s*disable\\s*\\("
    );
    private static final Pattern CORS_WILDCARD = Pattern.compile(
            "(allowedOrigins|allowedOriginPatterns|addAllowedOrigin|addAllowedOriginPattern)\\s*\\(\\s*\"\\*\""
                    + "|setAllowedOrigins\\s*\\(\\s*(List\\.of|Arrays\\.asList|Collections\\.singletonList)\\s*\\(\\s*\"\\*\""
                    + "|@CrossOrigin\\s*(\\(\\s*\\)|\\(\\s*(origins\\s*=\\s*)?\"\\*\"|(?!\\s*\\())"
    );
    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|api[-_.]?key|private[-_.]?key|access[-_.]?key|credentials)"
    );
    private static final Pattern NOT_A_SECRET_KEY = Pattern.compile(
            "(?i)(expir|ttl|length|enabled|header|name|url|uri|endpoint|path|type|validity|lifetime|timeout"
                    + "|prefix|policy|strength|encoder|algorithm|file|location)"
    );
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([^}:]+)(?::([^}]*))?}$");
    private static final Set<String> PLACEHOLDER_VALUES = Set.of(
            "", "null", "~", "true", "false", "changeme", "change-me", "xxx", "***", "<secret>", "example"
    );
    private static final Pattern YAML_ENTRY = Pattern.compile("^(\\s*)(?:-\\s+)?([\\w.\\-\"']+)\\s*:\\s*(.*)$");
    private static final Pattern YAML_ENV_ITEM = Pattern.compile("^\\s*-\\s*([\\w.\\-]+)=(.*)$");
    private static final Pattern PROPERTIES_ENTRY = Pattern.compile("^\\s*([\\w.\\-\\[\\]]+)\\s*[=:]\\s*(.*)$");
    private static final String ACTUATOR_EXPOSURE = "management.endpoints.web.exposure.include";

    private static final Map<String, String> DEPENDENCY_LABELS = dependencyLabels();

    private final GitService gitService;

    public RepositorySnapshot capture(String repositoryUrl, String branch) {
        try {
            return scan(gitService.getRepositoryPath(repositoryUrl, branch));
        } catch (IOException | RuntimeException exception) {
            log.warn("Не удалось собрать состояние кода {} ({}): {}", repositoryUrl, branch, exception.getMessage());
            return RepositorySnapshot.unavailable();
        }
    }

    RepositorySnapshot scan(Path root) throws IOException {
        List<String> files = listFiles(root);
        boolean truncated = files.size() >= MAX_VISITED_FILES;
        Reader reader = new Reader(root);

        Set<String> stack = new LinkedHashSet<>();
        boolean securityDependency = detectStack(files, reader, stack);

        List<String> mainCode = new ArrayList<>();
        int testCode = 0;
        for (String file : files) {
            if (CODE_EXTENSIONS.contains(extension(file))) {
                if (TEST_PATH.matcher(file).find()) {
                    testCode++;
                } else {
                    mainCode.add(file);
                }
            }
        }

        List<String> controllers = new ArrayList<>();
        boolean securityConfiguration = false;
        int methodSecurity = 0;
        boolean csrfDisabled = false;
        boolean corsWildcard = false;
        for (String file : mainCode) {
            if (!file.endsWith(".java") && !file.endsWith(".kt")) {
                continue;
            }
            String text = reader.read(file);
            if (text == null) {
                continue;
            }
            if (CONTROLLER.matcher(text).find()) {
                controllers.add(baseName(file));
            }
            securityConfiguration |= SECURITY_CONFIGURATION.matcher(text).find();
            methodSecurity += count(METHOD_SECURITY.matcher(text));
            csrfDisabled |= CSRF_DISABLED.matcher(text).find();
            corsWildcard |= CORS_WILDCARD.matcher(text).find();
        }

        ConfigScan config = scanConfiguration(files, reader);
        List<String> envFiles = files.stream()
                .filter(this::isCommittedEnvFile)
                .limit(MAX_LISTED)
                .toList();

        return new RepositorySnapshot(
                true,
                description(files, reader),
                List.copyOf(stack),
                modules(mainCode),
                mainCode.size(),
                testCode,
                controllers.stream().sorted().limit(MAX_LISTED).toList(),
                new SecurityFacts(
                        securityDependency,
                        securityConfiguration,
                        methodSecurity,
                        csrfDisabled,
                        corsWildcard,
                        config.actuatorExposesAll(),
                        config.secrets(),
                        envFiles
                ),
                truncated || reader.limitReached()
        );
    }

    private List<String> listFiles(Path root) throws IOException {
        List<String> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                return !directory.equals(root) && SKIPPED_DIRECTORIES.contains(directory.getFileName().toString())
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile()) {
                    files.add(root.relativize(file).toString().replace('\\', '/'));
                }
                return files.size() >= MAX_VISITED_FILES ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception) {
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparingInt((String path) -> path.split("/").length).thenComparing(path -> path));
        return files;
    }

    /** Возвращает признак зависимости Spring Security, заполняя стек. */
    private boolean detectStack(List<String> files, Reader reader, Set<String> stack) {
        boolean security = false;
        StringBuilder build = new StringBuilder();
        for (String file : files) {
            String name = fileName(file);
            if (name.equals("pom.xml")) {
                stack.add("Maven");
            } else if (name.equals("build.gradle") || name.equals("build.gradle.kts")) {
                stack.add("Gradle");
            } else if (!name.equals("package.json")) {
                if (name.equals("Dockerfile") || name.matches("(docker-)?compose.*\\.ya?ml")) {
                    stack.add("Docker");
                }
                continue;
            }
            String text = reader.read(file);
            if (text != null) {
                build.append(text).append('\n');
            }
        }

        String text = build.toString();
        Matcher java = Pattern.compile(
                "<(?:java\\.version|maven\\.compiler\\.release|maven\\.compiler\\.source)>(\\d+)"
                        + "|JavaLanguageVersion\\.of\\((\\d+)\\)|sourceCompatibility\\s*=\\s*['\"]?(?:JavaVersion\\.VERSION_)?(\\d+)"
        ).matcher(text);
        if (java.find()) {
            String version = java.group(1) != null ? java.group(1) : java.group(2) != null ? java.group(2) : java.group(3);
            stack.add("Java " + version);
        }
        Matcher boot = Pattern.compile(
                "spring-boot-starter-parent</artifactId>\\s*<version>([^<]+)</version>"
                        + "|org\\.springframework\\.boot['\"]?\\)?\\s*version\\s*['\"]([^'\"]+)['\"]"
        ).matcher(text);
        if (boot.find()) {
            stack.add("Spring Boot " + (boot.group(1) != null ? boot.group(1) : boot.group(2)).strip());
        } else if (text.contains("spring-boot")) {
            stack.add("Spring Boot");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        DEPENDENCY_LABELS.forEach((marker, label) -> {
            if (lower.contains(marker)) {
                stack.add(label);
            }
        });
        if (lower.contains("spring-boot-starter-security") || lower.contains("spring-security-")) {
            security = true;
            stack.add("Spring Security");
        }
        return security;
    }

    private String description(List<String> files, Reader reader) {
        String readme = files.stream()
                .filter(file -> !file.contains("/") && README.matcher(file).matches())
                .findFirst()
                .map(reader::read)
                .orElse(null);
        if (readme == null) {
            return null;
        }
        for (String paragraph : readme.split("\\R\\s*\\R")) {
            String cleaned = paragraph
                    .replaceAll("!\\[[^]]*]\\([^)]*\\)", "")
                    .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                    .replaceAll("<[^>]+>", "")
                    .replaceAll("[*_`>#]", "")
                    .replaceAll("\\s+", " ")
                    .strip();
            if (cleaned.length() >= 30 && !cleaned.startsWith("```") && !cleaned.startsWith("|")) {
                return cleaned.length() > MAX_DESCRIPTION_LENGTH
                        ? cleaned.substring(0, MAX_DESCRIPTION_LENGTH) + "…"
                        : cleaned;
            }
        }
        return null;
    }

    /** Модули — пакеты сразу под общим корнем, для других языков — каталоги верхнего уровня. */
    private List<ModuleFact> modules(List<String> mainCode) {
        List<List<String>> packages = mainCode.stream()
                .filter(file -> file.contains("src/main/java/") || file.contains("src/main/kotlin/"))
                .map(file -> List.of(file.substring(file.indexOf("src/main/") + "src/main/".length()).split("/")))
                .map(segments -> segments.subList(1, segments.size() - 1))
                .toList();

        Map<String, Integer> counts = new HashMap<>();
        if (packages.size() * 2 >= mainCode.size() && !packages.isEmpty()) {
            int common = commonPrefix(packages);
            for (List<String> segments : packages) {
                if (segments.size() > common) {
                    counts.merge(segments.get(common), 1, Integer::sum);
                }
            }
        } else {
            for (String file : mainCode) {
                int slash = file.indexOf('/');
                counts.merge(slash < 0 ? "(корень)" : file.substring(0, slash), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_MODULES)
                .map(entry -> new ModuleFact(entry.getKey(), entry.getValue()))
                .toList();
    }

    private int commonPrefix(List<List<String>> packages) {
        List<String> first = packages.getFirst();
        int length = first.size();
        for (List<String> segments : packages) {
            int index = 0;
            while (index < Math.min(length, segments.size()) && segments.get(index).equals(first.get(index))) {
                index++;
            }
            length = index;
        }
        // Хотя бы один уровень должен остаться для модулей.
        int deepest = packages.stream().mapToInt(List::size).max().orElse(0);
        return Math.min(length, Math.max(0, deepest - 1));
    }

    private ConfigScan scanConfiguration(List<String> files, Reader reader) {
        Set<String> secrets = new LinkedHashSet<>();
        boolean actuatorExposesAll = false;
        for (String file : files) {
            String extension = extension(file);
            boolean yaml = extension.equals("yml") || extension.equals("yaml");
            if ((!yaml && !extension.equals("properties")) || TEST_PATH.matcher(file).find()
                    || file.startsWith(".github/") || file.startsWith(".gitlab")) {
                continue;
            }
            String text = reader.read(file);
            if (text == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : (yaml ? yamlEntries(text) : propertiesEntries(text))) {
                String key = entry.getKey();
                String value = effectiveValue(entry.getValue());
                if (key.equalsIgnoreCase(ACTUATOR_EXPOSURE) && "*".equals(value)) {
                    actuatorExposesAll = true;
                }
                String lastSegment = key.substring(key.lastIndexOf('.') + 1);
                if (value != null && SECRET_KEY.matcher(lastSegment).find()
                        && !NOT_A_SECRET_KEY.matcher(lastSegment).find()
                        && secrets.size() < MAX_LISTED) {
                    secrets.add(file + ": " + key);
                }
            }
        }
        return new ConfigScan(List.copyOf(secrets), actuatorExposesAll);
    }

    private List<Map.Entry<String, String>> yamlEntries(String text) {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        Deque<Map.Entry<Integer, String>> path = new ArrayDeque<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            Matcher envItem = YAML_ENV_ITEM.matcher(line);
            if (envItem.matches()) {
                entries.add(Map.entry(joined(path, envItem.group(1)), envItem.group(2)));
                continue;
            }
            Matcher entry = YAML_ENTRY.matcher(line);
            if (!entry.matches()) {
                continue;
            }
            int indent = entry.group(1).length();
            while (!path.isEmpty() && path.peekLast().getKey() >= indent) {
                path.removeLast();
            }
            String key = entry.group(2).replace("\"", "").replace("'", "");
            String value = entry.group(3).strip();
            if (value.isEmpty() || value.equals("|") || value.equals(">")) {
                path.addLast(Map.entry(indent, key));
            } else {
                entries.add(Map.entry(joined(path, key), value));
            }
        }
        return entries;
    }

    private String joined(Deque<Map.Entry<Integer, String>> path, String key) {
        StringBuilder result = new StringBuilder();
        path.forEach(segment -> result.append(segment.getValue()).append('.'));
        return result.append(key).toString();
    }

    private List<Map.Entry<String, String>> propertiesEntries(String text) {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            Matcher entry = PROPERTIES_ENTRY.matcher(line);
            if (entry.matches()) {
                entries.add(Map.entry(entry.group(1), entry.group(2)));
            }
        }
        return entries;
    }

    /** Значение, реально попадающее в приложение; {@code null}, если секрета в файле нет. */
    private String effectiveValue(String raw) {
        String value = raw.replaceAll("\\s+#.*$", "").strip();
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1).strip();
        }
        if (value.startsWith("${{")) {
            return null;
        }
        Matcher placeholder = PLACEHOLDER.matcher(value);
        if (placeholder.matches()) {
            value = placeholder.group(2) == null ? "" : placeholder.group(2).strip();
        }
        if (value.equals("*")) {
            return value;
        }
        return PLACEHOLDER_VALUES.contains(value.toLowerCase(Locale.ROOT)) || value.startsWith("${")
                ? null
                : value;
    }

    private boolean isCommittedEnvFile(String file) {
        String name = fileName(file).toLowerCase(Locale.ROOT);
        return (name.equals(".env") || name.startsWith(".env."))
                && !name.matches("\\.env\\.(example|sample|template|dist|defaults)");
    }

    private int count(Matcher matcher) {
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String fileName(String file) {
        return file.substring(file.lastIndexOf('/') + 1);
    }

    private String baseName(String file) {
        String name = fileName(file);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String extension(String file) {
        String name = fileName(file);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static Map<String, String> dependencyLabels() {
        Map<String, String> labels = new java.util.LinkedHashMap<>();
        labels.put("kotlin", "Kotlin");
        labels.put("spring-boot-starter-web\"", "Spring Web");
        labels.put("spring-boot-starter-web<", "Spring Web");
        labels.put("spring-boot-starter-web:", "Spring Web");
        labels.put("spring-boot-starter-webflux", "Spring WebFlux");
        labels.put("spring-boot-starter-data-jpa", "Spring Data JPA");
        labels.put("postgresql", "PostgreSQL");
        labels.put("mysql", "MySQL");
        labels.put("mariadb", "MariaDB");
        labels.put("sqlite", "SQLite");
        labels.put("com.h2database", "H2");
        labels.put("mongodb", "MongoDB");
        labels.put("redis", "Redis");
        labels.put("kafka", "Kafka");
        labels.put("amqp", "RabbitMQ");
        labels.put("flyway", "Flyway");
        labels.put("liquibase", "Liquibase");
        labels.put("oauth2", "OAuth2");
        labels.put("keycloak", "Keycloak");
        labels.put("jjwt", "JWT");
        labels.put("java-jwt", "JWT");
        labels.put("nimbus-jose-jwt", "JWT");
        labels.put("springdoc", "OpenAPI");
        labels.put("\"react\"", "React");
        labels.put("\"vue\"", "Vue");
        labels.put("\"@angular/core\"", "Angular");
        labels.put("\"next\"", "Next.js");
        labels.put("\"express\"", "Express");
        labels.put("\"@nestjs/core\"", "NestJS");
        return labels;
    }

    private record ConfigScan(List<String> secrets, boolean actuatorExposesAll) {
    }

    /** Читает небольшие текстовые файлы с общим лимитом на число прочтений. */
    private static final class Reader {

        private final Path root;
        private final Map<String, String> cache = new HashMap<>();
        private int reads;
        private boolean limitReached;

        private Reader(Path root) {
            this.root = root;
        }

        String read(String file) {
            if (cache.containsKey(file)) {
                return cache.get(file);
            }
            if (reads >= MAX_READ_FILES) {
                limitReached = true;
                return null;
            }
            reads++;
            String text = null;
            try {
                Path path = root.resolve(file);
                if (Files.size(path) <= MAX_FILE_BYTES) {
                    text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                }
            } catch (IOException ignored) {
                // Недоступный файл просто не участвует в сводке.
            }
            cache.put(file, text);
            return text;
        }

        boolean limitReached() {
            return limitReached;
        }
    }
}
