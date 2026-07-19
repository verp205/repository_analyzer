package com.ivan_naumenko.repository_analyzer.git;

import com.ivan_naumenko.repository_analyzer.git.config.DirectoryProperties;
import com.ivan_naumenko.repository_analyzer.git.exception.RepositorySyncException;
import com.ivan_naumenko.repository_analyzer.git.model.GitCommit;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void clonesRepositoryAndUpdatesExistingCopy() throws Exception, RepositorySyncException {
        Path sourceDirectory = tempDirectory.resolve("source");
        Path clonesDirectory = tempDirectory.resolve("clones");
        createSourceRepository(sourceDirectory);

        DirectoryProperties properties = new DirectoryProperties(clonesDirectory);
        GitService gitService = new GitService(properties);
        String repositoryUrl = sourceDirectory.toUri().toString();

        Path firstClone = gitService.cloneOrUpdateRepository(repositoryUrl, "main");

        assertEquals(
                firstClone,
                gitService.getRepositoryPath(repositoryUrl, "main")
        );
        assertTrue(firstClone.startsWith(clonesDirectory.toAbsolutePath().normalize()));
        assertTrue(Files.isDirectory(firstClone.resolve(".git")));
        assertTrue(Files.isRegularFile(firstClone.resolve("README.md")));

        try (Git clonedGit = Git.open(firstClone.toFile())) {
            assertEquals("main", clonedGit.getRepository().getBranch());
            assertEquals(1, countCommits(clonedGit));
        }

        Path marker = firstClone.resolve("clone-was-reused.marker");
        Files.writeString(marker, "created after the first clone");
        addSecondCommit(sourceDirectory);

        Path secondClone = gitService.cloneOrUpdateRepository(repositoryUrl, "main");

        assertEquals(firstClone, secondClone);
        assertTrue(Files.isRegularFile(marker));
        assertTrue(Files.isRegularFile(secondClone.resolve("CHANGELOG.md")));
        assertEquals("Second version\n", Files.readString(secondClone.resolve("CHANGELOG.md")));

        try (Git updatedGit = Git.open(secondClone.toFile())) {
            assertEquals(2, countCommits(updatedGit));
            assertEquals("Second commit", updatedGit.log().call().iterator().next().getFullMessage());
        }
    }

    @Test
    void readsCommitHistoryWithAuthorsAndParentHashes() throws Exception, RepositorySyncException {
        Path sourceDirectory = tempDirectory.resolve("history-source");
        Path clonesDirectory = tempDirectory.resolve("history-clones");
        createSourceRepository(sourceDirectory);
        addSecondCommit(sourceDirectory);

        DirectoryProperties properties = new DirectoryProperties(clonesDirectory);
        GitService gitService = new GitService(properties);
        String repositoryUrl = sourceDirectory.toUri().toString();
        gitService.cloneOrUpdateRepository(repositoryUrl, "main");

        List<GitCommit> history = gitService.readHistory(repositoryUrl, "main");

        assertEquals(2, history.size());

        GitCommit newestCommit = history.get(0);
        GitCommit rootCommit = history.get(1);

        assertEquals("Second commit", newestCommit.message());
        assertEquals("Initial commit", rootCommit.message());
        assertEquals(40, newestCommit.commitHash().length());
        assertEquals(40, rootCommit.commitHash().length());
        assertFalse(newestCommit.commitHash().isBlank());
        assertFalse(rootCommit.commitHash().isBlank());

        assertEquals(List.of(rootCommit.commitHash()), newestCommit.parentHashes());
        assertTrue(rootCommit.parentHashes().isEmpty());

        assertEquals("Test User", newestCommit.authorName());
        assertEquals("test@example.com", newestCommit.authorMail());
        assertNotNull(newestCommit.committedAt());
    }

    @Test
    void serializesConcurrentCloneRequestsForSameRepository() throws Exception {
        Path sourceDirectory = tempDirectory.resolve("concurrent-source");
        Path clonesDirectory = tempDirectory.resolve("concurrent-clones");
        createSourceRepository(sourceDirectory);

        GitService gitService = new GitService(new DirectoryProperties(clonesDirectory));
        String repositoryUrl = sourceDirectory.toUri().toString();
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Path> first = callers.submit(() -> {
                start.await();
                return gitService.cloneOrUpdateRepository(repositoryUrl, "main");
            });
            Future<Path> second = callers.submit(() -> {
                start.await();
                return gitService.cloneOrUpdateRepository(repositoryUrl, "main");
            });

            start.countDown();

            Path firstResult = first.get(10, TimeUnit.SECONDS);
            Path secondResult = second.get(10, TimeUnit.SECONDS);

            assertEquals(firstResult, secondResult);
            assertTrue(Files.isDirectory(firstResult.resolve(".git")));
        } finally {
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void createSourceRepository(Path sourceDirectory) throws Exception {
        try (Git sourceGit = Git.init()
                .setDirectory(sourceDirectory.toFile())
                .setInitialBranch("main")
                .call()) {
            Files.writeString(sourceDirectory.resolve("README.md"), "# Test repository\n");

            sourceGit.add()
                    .addFilepattern("README.md")
                    .call();

            sourceGit.commit()
                    .setMessage("Initial commit")
                    .setAuthor("Test User", "test@example.com")
                    .setCommitter("Test User", "test@example.com")
                    .call();
        }
    }

    private void addSecondCommit(Path sourceDirectory) throws Exception {
        try (Git sourceGit = Git.open(sourceDirectory.toFile())) {
            Files.writeString(sourceDirectory.resolve("CHANGELOG.md"), "Second version\n");

            sourceGit.add()
                    .addFilepattern("CHANGELOG.md")
                    .call();

            sourceGit.commit()
                    .setMessage("Second commit")
                    .setAuthor("Test User", "test@example.com")
                    .setCommitter("Test User", "test@example.com")
                    .call();
        }
    }

    private int countCommits(Git git) throws Exception {
        int commitCount = 0;
        for (RevCommit ignored : git.log().call()) {
            commitCount++;
        }
        return commitCount;
    }
}
