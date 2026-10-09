package com.ivan_naumenko.repository_analyzer.report.snapshot;

import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import com.ivan_naumenko.repository_analyzer.cache.BoundedSingleFlightCache;
import com.ivan_naumenko.repository_analyzer.code.TextCodeLanguages;
import com.ivan_naumenko.repository_analyzer.report.snapshot.RepositorySnapshot.ModuleFact;
import com.ivan_naumenko.repository_analyzer.report.snapshot.RepositorySnapshot.SecurityFacts;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
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
    private static final Pattern README = Pattern.compile("(?i)readme(\\.(md|rst|txt|adoc))?");
    private static final Pattern TEST_PATH = Pattern.compile(
            "(^|/)(?i:src/test|test|tests|__tests__|spec)/|(Test|Tests|IT)\\.[a-z]+$|\\.(test|spec)\\.[a-z]+$"
                    + "|_test\\.go$|(^|/)test_[^/]+\\.py$|_spec\\.rb$"
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
    private final BoundedSingleFlightCache<String, RepositorySnapshot> snapshots = new BoundedSingleFlightCache<>(32);

    public String headCommitHash(String repositoryUrl, String branch) {
        try (Git git = Git.open(gitService.getRepositoryPath(repositoryUrl, branch).toFile())) {
            var head = git.getRepository().resolve("HEAD");
            return head == null ? null : head.name();
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    public RepositorySnapshot capture(String repositoryUrl, String branch) {
        return capture(repositoryUrl, branch, null);
    }

    public RepositorySnapshot capture(String repositoryUrl, String branch, String commitHash) {
        Path root;
        try {
            root = gitService.getRepositoryPath(repositoryUrl, branch);
            try (Git git = Git.open(root.toFile())) {
                ObjectId head = commitHash == null ? git.getRepository().resolve("HEAD") : ObjectId.fromString(commitHash);
                if (head == null) return RepositorySnapshot.unavailable();
                String key = root.toRealPath() + ":" + head.name();
                return snapshots.get(key, () -> {
                    try { return scanCommit(git.getRepository(), head); }
                    catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
                });
            }
        } catch (IOException | RuntimeException exception) {
            log.warn("Не удалось собрать состояние кода {} ({}): {}", repositoryUrl, branch, exception.getMessage());
            return RepositorySnapshot.unavailable();
        }
    }

    RepositorySnapshot scan(Path root) throws IOException {
        List<String> files = listFiles(root);
        return scan(files, new Reader(root));
    }

    private RepositorySnapshot scanCommit(Repository repository, ObjectId head) throws IOException {
        Map<String, ObjectId> blobs = new HashMap<>();
        try (RevWalk walk = new RevWalk(repository); TreeWalk tree = new TreeWalk(repository)) {
            tree.addTree(walk.parseCommit(head).getTree());
            while (tree.next()) {
                if (tree.isSubtree()) {
                    if (!SKIPPED_DIRECTORIES.contains(tree.getNameString())) tree.enterSubtree();
                    continue;
                }
                String path = tree.getPathString();
                if (tree.getFileMode(0).getObjectType() != org.eclipse.jgit.lib.Constants.OBJ_BLOB
                        || tree.getFileMode(0).equals(org.eclipse.jgit.lib.FileMode.SYMLINK)) continue;
                blobs.put(path, tree.getObjectId(0));
                if (blobs.size() >= MAX_VISITED_FILES) break;
            }
        }
        List<String> files = new ArrayList<>(blobs.keySet());
        files.sort(Comparator.comparingInt((String path) -> path.split("/").length).thenComparing(path -> path));
        return scan(files, new Reader(repository, blobs));
    }

    private RepositorySnapshot scan(List<String> files, Reader reader) {
        boolean truncated = files.size() >= MAX_VISITED_FILES;

        Set<String> stack = new LinkedHashSet<>();
        boolean securityDependency = detectStack(files, reader, stack);

        List<String> mainCode = new ArrayList<>();
        int testCode = 0;
        for (String file : files) {
            if (TextCodeLanguages.knownSource(file) && !isIgnoredText(file)) {
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

        String description = description(files, reader);
        List<String> originalSources = reader.cache.entrySet().stream().filter(entry -> entry.getValue() != null)
                .map(Map.Entry::getKey).sorted().limit(100).toList();
        // A few actual source files provide evidence for non-Java repositories too.
        mainCode.stream().filter(file -> !file.endsWith(".java") && !file.endsWith(".kt")).limit(40).forEach(reader::read);
        Map<String, Integer> languageCounts = new java.util.TreeMap<>();
        for (String file : files) {
            if (isIgnoredText(file)) continue;
            String language = TextCodeLanguages.detect(file, null);
            if (language.startsWith("Text")) {
                String content = reader.read(file);
                if (content == null) continue;
                language = TextCodeLanguages.detect(file, content);
                if (!language.equals("Binary") && (TextCodeLanguages.source(file, content) || language.equals("Text"))) {
                    if (TEST_PATH.matcher(file).find()) testCode++;
                    else mainCode.add(file);
                }
            }
            if (TextCodeLanguages.source(file, reader.cache.get(file)) || language.equals("Text")) languageCounts.merge(language, 1, Integer::sum);
        }
        List<String> sourcePaths = java.util.stream.Stream.concat(originalSources.stream(), reader.cache.entrySet().stream()
                .filter(entry -> entry.getValue() != null).map(Map.Entry::getKey).sorted()).distinct().limit(100).toList();

        return new RepositorySnapshot(
                true,
                description,
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
                truncated || reader.limitReached(),
                sourcePaths,
                languageCounts.entrySet().stream().map(entry -> new RepositorySnapshot.LanguageFact(entry.getKey(), entry.getValue())).toList(),
                files.stream().filter(this::isManifest).toList(),
                "Diff всех текстовых файлов, включая неизвестные расширения. Java — дополнительный AST-разбор. "
                        + "Снимок — текстовые признаки, проверки защиты Spring относятся только к Java/Kotlin. "
                        + "Код и тесты не запускались; отсутствие признака не доказывает отсутствие риска."
        );
    }

    private boolean isIgnoredText(String file) {
        String name = fileName(file).toLowerCase(Locale.ROOT);
        if (isManifest(file) && !Set.of("build.gradle", "build.gradle.kts", "setup.py", "Dockerfile", "Makefile").contains(fileName(file))) return true;
        return name.endsWith(".lock") || name.equals("package-lock.json") || name.endsWith(".svg")
                || name.equals(".env") || name.startsWith(".env.") || name.endsWith(".csproj") || name.endsWith(".fsproj")
                || name.endsWith(".adoc") || name.startsWith("license") || name.equals(".gitignore");
    }

    private boolean isManifest(String path) {
        String name = fileName(path);
        return Set.of("pom.xml", "build.gradle", "build.gradle.kts", "package.json", "pyproject.toml", "requirements.txt",
                "Pipfile", "setup.py", "setup.cfg", "go.mod", "Cargo.toml", "Gemfile", "composer.json", "Package.swift",
                "pubspec.yaml", "CMakeLists.txt", "Makefile", "Dockerfile").contains(name)
                || name.endsWith(".csproj") || name.endsWith(".fsproj");
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
            } else if (isManifest(file)) {
                if (Set.of("pyproject.toml", "requirements.txt", "Pipfile", "setup.py", "setup.cfg").contains(name)) stack.add("Python");
                if (name.equals("go.mod")) stack.add("Go modules");
                if (name.equals("Cargo.toml")) stack.add("Rust / Cargo");
                if (name.equals("Gemfile")) stack.add("Ruby / Bundler");
                if (name.equals("composer.json")) stack.add("PHP / Composer");
                if (name.endsWith(".csproj") || name.endsWith(".fsproj")) stack.add(".NET");
                if (name.equals("Package.swift")) stack.add("Swift Package Manager");
                if (name.equals("pubspec.yaml")) stack.add("Dart / pub");
                if (name.equals("CMakeLists.txt")) stack.add("CMake");
                if (name.equals("Makefile")) stack.add("Make");
                if (name.equals("Dockerfile")) stack.add("Docker");
                if (name.equals("package.json")) stack.add("Node.js");
            } else {
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
        private final Repository repository;
        private final Map<String, ObjectId> blobs;
        private final Map<String, String> cache = new HashMap<>();
        private int reads;
        private boolean limitReached;

        private Reader(Path root) {
            this.root = root;
            this.repository = null;
            this.blobs = Map.of();
        }

        private Reader(Repository repository, Map<String, ObjectId> blobs) {
            this.root = null;
            this.repository = repository;
            this.blobs = blobs;
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
                if (repository != null) {
                    var blob = repository.open(blobs.get(file));
                    if (blob.getSize() <= MAX_FILE_BYTES) text = new String(blob.getBytes(), StandardCharsets.UTF_8);
                    else limitReached = true;
                } else {
                    Path path = root.resolve(file);
                    if (Files.size(path) <= MAX_FILE_BYTES) text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                    else limitReached = true;
                }
                if (text != null && text.indexOf('\0') >= 0) text = null;
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
