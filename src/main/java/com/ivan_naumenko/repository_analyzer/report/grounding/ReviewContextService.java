package com.ivan_naumenko.repository_analyzer.report.grounding;

import com.ivan_naumenko.repository_analyzer.diff.repository.CommitDiffRepository;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.Citation;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ReviewContextService {
    private static final int DIFF_BUDGET = 6_000, CONTEXT_BUDGET = 4_000, MAX_BLOB_BYTES = 262_144;
    private static final Pattern HUNK = Pattern.compile("(?m)^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");
    private static final Pattern SYMBOL = Pattern.compile("\\b([A-Za-z_][A-Za-z_0-9]{3,})\\s*\\(");
    private static final Set<String> SKIP = Set.of(".git", "node_modules", "vendor", "target", "build", "dist", ".venv", "venv");
    private final CommitDiffRepository diffs;
    private final GitService git;

    public ReviewContext load(ReportGenerationData report, int commitId) {
        // The commit IDs are pinned to the report; a moving branch must not change its evidence.
        return read(report, commitId);
    }

    private ReviewContext read(ReportGenerationData report, int commitId) {
        var rows = diffs.findForContext(List.of(commitId));
        if (rows.size() != 1 || !rows.getFirst().getCommit().getAnalysisTask().getId().equals(report.taskId()))
            throw new IllegalStateException("Diff не принадлежит задаче отчёта");
        var diff = rows.getFirst();
        String hash = diff.getCommit().getCommitHash();
        var files = diff.getChangedFiles().stream().map(file -> new ReviewContext.SourceFile(file.getOldPath(),
                file.getNewPath() == null ? file.getOldPath() : file.getNewPath(), file.getChangeType().name(),
                file.getAddedLines(), file.getDeletedLines(), file.getPatch())).toList();
        List<Citation> citations = new ArrayList<>();
        Set<String> inspected = new HashSet<>(), limited = new HashSet<>();
        List<String> limitations = new ArrayList<>();
        int remaining = DIFF_BUDGET;
        for (var file : files) {
            cancelled();
            if (file.patch().isBlank() || file.patch().contains("GIT binary patch") || file.patch().contains("Binary files ")) {
                limited.add(file.path()); continue;
            }
            String excerpt = completeLines(file.patch(), Math.min(1_800, remaining));
            if (excerpt.isEmpty()) { limited.add(file.path()); continue; }
            citations.add(new Citation("D" + citations.size(), "C" + commitId, file.path(), hash,
                    "DIFF", 1, excerpt.split("\n", -1).length, excerpt));
            remaining -= excerpt.length();
            inspected.add(file.path());
            if (excerpt.length() < file.patch().length()) limited.add(file.path());
        }
        int contextFiles = 0;
        try (Git checkout = Git.open(git.getRepositoryPath(report.repositoryUrl(), report.branch()).toFile());
             RevWalk walk = new RevWalk(checkout.getRepository())) {
            Repository repository = checkout.getRepository();
            var after = walk.parseCommit(ObjectId.fromString(hash));
            var before = diff.getParentHash() == null ? null : walk.parseCommit(ObjectId.fromString(diff.getParentHash()));
            int contextRemaining = CONTEXT_BUDGET;
            Set<String> candidates = new LinkedHashSet<>();
            Set<String> symbols = new LinkedHashSet<>();
            for (var file : files) {
            cancelled();
                var symbolsMatcher = SYMBOL.matcher(file.patch());
                while (symbolsMatcher.find() && symbols.size() < 8) symbols.add(symbolsMatcher.group(1));
                var hunk = HUNK.matcher(file.patch());
                if (!hunk.find()) continue;
                for (boolean old : List.of(true, false)) {
                    if (contextRemaining < 200 || old && before == null) continue;
                    String path = old ? file.oldPath() : file.path();
                    if (path == null || path.equals("/dev/null") || !old && file.changeType().equals("DELETE")) continue;
                    var revision = old ? before : after;
                    String source = blob(repository, revision.getTree(), path);
                    if (source == null) continue;
                    int first = Math.max(1, Integer.parseInt(hunk.group(old ? 1 : 2)) - 12);
                    Citation citation = window("Q" + commitId + "_" + citations.size(), path, revision.name(),
                            old ? "BEFORE" : "AFTER", source, first, Math.min(1_400, contextRemaining));
                    if (citation != null) { citations.add(citation); contextRemaining -= citation.code().length(); candidates.add(path); }
                }
            }
            // A bounded lexical search supplies surrounding callers/tests without claiming type-resolved links.
            if (contextRemaining >= 200 && !symbols.isEmpty()) {
                int visited = 0, read = 0, related = 0;
                Set<String> changed = new HashSet<>(); files.forEach(file -> changed.add(file.path()));
                try (TreeWalk tree = new TreeWalk(repository)) {
                    tree.addTree(after.getTree());
                    while (tree.next() && visited++ < 20_000 && read < 40 && related < 2) {
                        cancelled();
                        if (tree.isSubtree()) { if (!SKIP.contains(tree.getNameString())) tree.enterSubtree(); continue; }
                        String path = tree.getPathString();
                        if (changed.contains(path) || tree.getFileMode(0).equals(FileMode.SYMLINK)) continue;
                        String lower = path.toLowerCase(Locale.ROOT);
                        boolean likely = files.stream().anyMatch(file -> lower.contains(stem(file.path()).toLowerCase(Locale.ROOT)))
                                || lower.contains("test") || lower.contains("spec");
                        if (!likely) continue;
                        read++;
                        String text = blob(repository, after.getTree(), path);
                        if (text == null) continue;
                        String[] lines = text.split("\n", -1);
                        int matched = -1;
                        for (int i = 0; i < lines.length && matched < 0; i++) {
                            String line = lines[i];
                            if (symbols.stream().anyMatch(symbol -> Pattern.compile("\\b" + Pattern.quote(symbol) + "\\s*\\(").matcher(line).find())) matched = i;
                        }
                        if (matched < 0) continue;
                        Citation citation = window("Q" + commitId + "_" + citations.size(), path, hash, "RELATED", text,
                                Math.max(1, matched - 8), Math.min(1_400, contextRemaining));
                        if (citation != null) { citations.add(citation); contextRemaining -= citation.code().length(); related++; candidates.add(path); }
                        if (contextRemaining < 200) break;
                    }
                }
            }
            contextFiles = candidates.size();
        } catch (java.util.concurrent.CancellationException cancelled) {
            throw cancelled;
        } catch (IOException | RuntimeException unavailable) {
            limitations.add("Дополнительный контекст Git недоступен; оценка ограничена сохранённым diff.");
        }
        if (!limited.isEmpty()) limitations.add("Не все изменённые файлы и строки вошли в повторный разбор.");
        limitations.add("Связанные фрагменты найдены по именам; полнота цепочки вызовов и выполнение тестов не подтверждены.");
        return new ReviewContext(commitId, hash, files, citations, inspected.size(), limited.size(), contextFiles, limitations);
    }

    private static String blob(Repository repository, ObjectId treeId, String path) throws IOException {
        try (TreeWalk tree = TreeWalk.forPath(repository, path, treeId)) {
            if (tree == null || tree.getFileMode(0).getObjectType() != Constants.OBJ_BLOB || tree.getFileMode(0).equals(FileMode.SYMLINK)) return null;
            var object = repository.open(tree.getObjectId(0));
            if (object.getSize() > MAX_BLOB_BYTES) return null;
            String text = new String(object.getBytes(), StandardCharsets.UTF_8);
            return text.indexOf('\0') >= 0 ? null : text;
        }
    }

    private static Citation window(String id, String path, String hash, String side, String text, int first, int budget) {
        String[] lines = text.split("\n", -1);
        if (text.length() <= budget) return new Citation(id, id, path, hash, side, 1, lines.length, text);
        if (first > lines.length || budget < 1) return null;
        String selection = String.join("\n", Arrays.copyOfRange(lines, first - 1, Math.min(lines.length, first + 28)));
        String code = completeLines(selection, budget);
        return code.isBlank() ? null : new Citation(id, id, path, hash, side, first, first + code.split("\n", -1).length - 1, code);
    }

    static String completeLines(String text, int limit) {
        if (limit <= 0) return "";
        if (text.length() <= limit) return text;
        int end = text.lastIndexOf('\n', limit);
        return end <= 0 ? "" : text.substring(0, end);
    }

    private static void cancelled() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Чтение контекста остановлено");
    }

    private static String stem(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1); int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
