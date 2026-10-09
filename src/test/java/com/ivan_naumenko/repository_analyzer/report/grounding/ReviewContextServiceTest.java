package com.ivan_naumenko.repository_analyzer.report.grounding;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import com.ivan_naumenko.repository_analyzer.diff.model.*;
import com.ivan_naumenko.repository_analyzer.diff.repository.CommitDiffRepository;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import com.ivan_naumenko.repository_analyzer.report.model.*;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewContextServiceTest {
    @TempDir Path directory;
    private final CommitDiffRepository diffs = mock(CommitDiffRepository.class);
    private final GitService gitService = mock(GitService.class);
    private final ReportGenerationData report = new ReportGenerationData(1, 4, ReportAudience.DEVELOPER, "repo", "main");

    @Test void readsBeforeAfterAndRelatedCodeAtPinnedVersionsEvenAfterHeadMoves() throws Exception {
        try (Git git = Git.init().setDirectory(directory.toFile()).call()) {
            String before = "def debit(amount):\n    if amount <= 0: raise ValueError()\n    balance.debit(amount)\n";
            String after = "def debit(amount):\n    balance.debit(amount)\n";
            String first = save(git, "payment.py", before);
            Files.writeString(directory.resolve("test_payment.py"), "def test_debit():\n    debit(-1)\n");
            String second = save(git, "payment.py", after);
            save(git, "payment.py", "UNRELATED_CURRENT_HEAD\n");
            var row = row(second, first, "payment.py", "@@ -1,3 +1,2 @@\n def debit(amount):\n-    if amount <= 0: raise ValueError()\n     balance.debit(amount)\n");
            when(diffs.findForContext(List.of(101))).thenReturn(List.of(row));
            when(gitService.getRepositoryPath("repo", "main")).thenReturn(directory);
            var context = new ReviewContextService(diffs, gitService).load(report, 101);
            var old = context.citations().stream().filter(c -> c.side().equals("BEFORE")).findFirst().orElseThrow();
            var current = context.citations().stream().filter(c -> c.side().equals("AFTER")).findFirst().orElseThrow();
            assertEquals(first, old.commitHash()); assertEquals(before, old.code());
            assertEquals(second, current.commitHash()); assertEquals(after, current.code());
            assertTrue(context.citations().stream().anyMatch(c -> c.side().equals("RELATED") && c.commitHash().equals(second)));
            assertTrue(context.citations().stream().noneMatch(c -> c.code().contains("UNRELATED_CURRENT_HEAD")));
            assertEquals(0, context.limitedFiles());
        }
    }
    @Test void marksOmittedBinaryAndTruncatedFilesAndDoesNotCacheMissingGit() throws Exception {
        var row = row("a".repeat(40), null, "large.unknown", "+a line of changed text\n".repeat(300));
        row.addChangedFile(file("image.bin", "Binary files a/image.bin and b/image.bin differ"));
        when(diffs.findForContext(List.of(101))).thenReturn(List.of(row));
        when(gitService.getRepositoryPath("repo", "main")).thenReturn(directory.resolve("absent"));
        var service = new ReviewContextService(diffs, gitService);
        var result = service.load(report, 101); service.load(report, 101);
        assertEquals(2, result.limitedFiles()); assertEquals(1, result.inspectedFiles());
        assertTrue(result.limitations().stream().anyMatch(value -> value.contains("Git недоступен")));
        verify(gitService, times(2)).getRepositoryPath("repo", "main");
    }
    @Test void rejectsDiffFromAnotherTask() {
        var row = row("a".repeat(40), null, "a.py", "+x"); row.getCommit().getAnalysisTask().setId(99);
        when(diffs.findForContext(List.of(101))).thenReturn(List.of(row));
        assertThrows(IllegalStateException.class, () -> new ReviewContextService(diffs, gitService).load(report, 101));
        verifyNoInteractions(gitService);
    }
    private CommitDiffEntity row(String hash, String parent, String path, String patch) {
        var task = new AnalysisTask(); task.setId(4);
        var commit = new RepositoryCommit(); commit.setId(101); commit.setCommitHash(hash); commit.setAnalysisTask(task);
        var row = new CommitDiffEntity(); row.setCommitId(101); row.setCommit(commit); row.setParentHash(parent); row.addChangedFile(file(path, patch)); return row;
    }
    private ChangedFileEntity file(String path, String patch) {
        var file = new ChangedFileEntity(); file.setOldPath(path); file.setNewPath(path); file.setChangeType(FileChangeType.MODIFY); file.setPatch(patch); return file;
    }
    private String save(Git git, String name, String content) throws Exception {
        Files.writeString(directory.resolve(name), content); git.add().addFilepattern(".").call();
        return git.commit().setMessage("change").setAuthor("Test", "test@example.com").call().name();
    }
}
