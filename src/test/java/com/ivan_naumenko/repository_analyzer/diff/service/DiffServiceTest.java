package com.ivan_naumenko.repository_analyzer.diff.service;

import com.ivan_naumenko.repository_analyzer.diff.exception.DiffReadException;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFile;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiff;
import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffServiceTest {

    @TempDir
    private Path repositoryDirectory;

    private final DiffService diffService = new DiffService();

    @Test
    void readsRootCommitAgainstEmptyTree() throws Exception {
        RevCommit rootCommit;

        try (Git git = Git.init()
                .setDirectory(repositoryDirectory.toFile())
                .call()) {
            Path javaFile = repositoryDirectory.resolve("src/Sample.java");
            Files.createDirectories(javaFile.getParent());
            Files.writeString(javaFile, """
                    package sample;

                    class Sample {
                    }
                    """);
            git.add().addFilepattern(".").call();
            rootCommit = commit(git, "Root commit");
        }

        CommitDiff diff = diffService.readDiff(
                repositoryDirectory,
                rootCommit.getName()
        );

        assertEquals(rootCommit.getName(), diff.commitHash());
        assertNull(diff.parentHash());
        assertTrue(diff.rootCommit());
        assertEquals(1, diff.changedFiles().size());

        ChangedFile file = diff.changedFiles().getFirst();
        assertNull(file.oldPath());
        assertEquals("src/Sample.java", file.newPath());
        assertEquals(FileChangeType.ADD, file.changeType());
        assertEquals(4, file.addedLines());
        assertEquals(0, file.deletedLines());
        assertTrue(file.javaFile());
        assertEquals("src/Sample.java", file.path());
        assertTrue(file.patch().contains("class Sample"));
        assertEquals(4, diff.totalAddedLines());
        assertEquals(0, diff.totalDeletedLines());
        assertEquals(file.patch(), diff.rawDiff());
    }

    @Test
    void readsCommitAgainstItsFirstParent() throws Exception {
        RevCommit rootCommit;
        RevCommit childCommit;

        try (Git git = Git.init()
                .setDirectory(repositoryDirectory.toFile())
                .call()) {
            Path javaFile = repositoryDirectory.resolve("Sample.java");
            Files.writeString(javaFile, """
                    class Sample {
                    }
                    """);
            git.add().addFilepattern(".").call();
            rootCommit = commit(git, "Root commit");

            Files.writeString(javaFile, """
                    class Sample {
                        private int value;
                    }
                    """);
            git.add().addFilepattern(".").call();
            childCommit = commit(git, "Add field");
        }

        CommitDiff diff = diffService.readDiff(
                repositoryDirectory,
                childCommit.getName()
        );

        assertEquals(childCommit.getName(), diff.commitHash());
        assertEquals(rootCommit.getName(), diff.parentHash());
        assertFalse(diff.rootCommit());
        assertEquals(1, diff.changedFiles().size());

        ChangedFile file = diff.changedFiles().getFirst();
        assertEquals("Sample.java", file.oldPath());
        assertEquals("Sample.java", file.newPath());
        assertEquals(FileChangeType.MODIFY, file.changeType());
        assertEquals(1, file.addedLines());
        assertEquals(0, file.deletedLines());
        assertTrue(file.javaFile());
        assertTrue(diff.rawDiff().contains("private int value"));
    }

    @Test
    void rejectsUnknownCommit() throws Exception {
        try (Git ignored = Git.init()
                .setDirectory(repositoryDirectory.toFile())
                .call()) {
        }

        assertThrows(
                DiffReadException.class,
                () -> diffService.readDiff(
                        repositoryDirectory,
                        "f".repeat(40)
                )
        );
    }

    @Test
    void rejectsDirectoryWithoutGitRepository() {
        assertThrows(
                DiffReadException.class,
                () -> diffService.readDiff(
                        repositoryDirectory,
                        "f".repeat(40)
                )
        );
    }

    private RevCommit commit(Git git, String message) throws Exception {
        return git.commit()
                .setMessage(message)
                .setAuthor("Test User", "test@example.com")
                .setCommitter("Test User", "test@example.com")
                .call();
    }
}
