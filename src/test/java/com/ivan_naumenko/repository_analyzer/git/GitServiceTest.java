package com.ivan_naumenko.repository_analyzer.git;

import com.ivan_naumenko.repository_analyzer.config.directory.DirectoryProperties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void clonesRepositoryAndUpdatesExistingCopy() throws Exception {
        Path sourceDirectory = tempDirectory.resolve("source");
        Path clonesDirectory = tempDirectory.resolve("clones");
        createSourceRepository(sourceDirectory);

        DirectoryProperties properties = new DirectoryProperties(clonesDirectory);
        GitService gitService = new GitService(properties);
        String repositoryUrl = sourceDirectory.toUri().toString();

        Path firstClone = gitService.cloneOrUpdateRepository(repositoryUrl, "main");

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
