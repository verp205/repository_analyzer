package com.ivan_naumenko.repository_analyzer.code;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Detection describes the input; unknown text is still eligible for diff-based review. */
public final class TextCodeLanguages {
    private static final Map<String, String> LANGUAGES = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("kt", "Kotlin"), Map.entry("kts", "Kotlin"),
            Map.entry("js", "JavaScript"), Map.entry("jsx", "JavaScript"), Map.entry("mjs", "JavaScript"), Map.entry("cjs", "JavaScript"),
            Map.entry("ts", "TypeScript"), Map.entry("tsx", "TypeScript"), Map.entry("vue", "Vue"), Map.entry("svelte", "Svelte"),
            Map.entry("py", "Python"), Map.entry("pyi", "Python"), Map.entry("go", "Go"), Map.entry("rs", "Rust"),
            Map.entry("rb", "Ruby"), Map.entry("php", "PHP"), Map.entry("cs", "C#"), Map.entry("fs", "F#"), Map.entry("fsx", "F#"),
            Map.entry("c", "C"), Map.entry("h", "C/C++ header"), Map.entry("cc", "C++"), Map.entry("cpp", "C++"), Map.entry("cxx", "C++"), Map.entry("hpp", "C++"),
            Map.entry("swift", "Swift"), Map.entry("m", "Objective-C"), Map.entry("mm", "Objective-C++"), Map.entry("dart", "Dart"),
            Map.entry("scala", "Scala"), Map.entry("groovy", "Groovy"), Map.entry("sql", "SQL"), Map.entry("sh", "Shell"),
            Map.entry("bash", "Shell"), Map.entry("zsh", "Shell"), Map.entry("ps1", "PowerShell"), Map.entry("lua", "Lua"),
            Map.entry("r", "R"), Map.entry("ex", "Elixir"), Map.entry("exs", "Elixir"), Map.entry("erl", "Erlang"),
            Map.entry("hs", "Haskell"), Map.entry("clj", "Clojure"), Map.entry("cljs", "ClojureScript"), Map.entry("pl", "Perl"),
            Map.entry("jl", "Julia"), Map.entry("zig", "Zig"), Map.entry("nim", "Nim"), Map.entry("sol", "Solidity"),
            Map.entry("tf", "Terraform"), Map.entry("html", "HTML"), Map.entry("css", "CSS"), Map.entry("scss", "SCSS"),
            Map.entry("xml", "XML"), Map.entry("json", "JSON"), Map.entry("yml", "YAML"), Map.entry("yaml", "YAML"),
            Map.entry("properties", "Properties"), Map.entry("toml", "TOML"), Map.entry("ini", "INI"), Map.entry("md", "Markdown"),
            Map.entry("rst", "reStructuredText"), Map.entry("txt", "Text"), Map.entry("proto", "Protocol Buffers"), Map.entry("graphql", "GraphQL")
    );
    private static final Set<String> DATA = Set.of("XML", "JSON", "YAML", "Properties", "TOML", "INI", "Markdown", "reStructuredText", "Text");
    private static final Set<String> BINARY = Set.of("png", "jpg", "jpeg", "gif", "webp", "ico", "pdf", "zip", "jar", "gz", "exe", "dll", "so", "class", "woff", "woff2", "mp4", "mp3", "db");
    private TextCodeLanguages() {}

    public static String detect(String path, String text) {
        String name = name(path);
        String extension = extension(path);
        if (BINARY.contains(extension) || text != null && (text.indexOf('\0') >= 0 || text.contains("GIT binary patch") || text.lines().anyMatch(line -> line.startsWith("Binary files ")))) return "Binary";
        if (name.equals("dockerfile") || name.startsWith("dockerfile.")) return "Dockerfile";
        if (name.equals("makefile") || name.equals("gnumakefile")) return "Makefile";
        if (LANGUAGES.containsKey(extension)) return LANGUAGES.get(extension);
        if (text != null) {
            for (String line : text.lines().limit(12).toList()) {
                String candidate = line.replaceFirst("^[+ ]", "").strip();
                if (!candidate.startsWith("#!")) continue;
                String shebang = candidate.toLowerCase(Locale.ROOT);
                if (shebang.contains("python")) return "Python";
                if (shebang.contains("node")) return "JavaScript";
                if (shebang.contains("ruby")) return "Ruby";
                if (shebang.contains("perl")) return "Perl";
                if (shebang.contains("bash") || shebang.endsWith("/sh") || shebang.contains("zsh")) return "Shell";
            }
        }
        return extension.isEmpty() ? "Text" : "Text (." + extension + ")";
    }

    public static boolean source(String path, String text) {
        String language = detect(path, text);
        return !language.equals("Binary") && (!DATA.contains(language) || language.startsWith("Text ("));
    }

    public static boolean knownSource(String path) {
        String language = detect(path, null);
        return source(path, null) && !language.startsWith("Text");
    }

    public static String extraContextLanguage(String path, String patch) {
        String language = detect(path, patch);
        // Java's original serialized context and prompts stay byte-for-byte compatible.
        return !language.equals("Java") && !language.equals("Binary") && (source(path, patch) || language.equals("Text")) ? language : null;
    }

    private static String name(String path) {
        return path == null ? "" : path.replace('\\', '/').substring(path.replace('\\', '/').lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
    }
    private static String extension(String path) {
        String name = name(path); int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }
}
