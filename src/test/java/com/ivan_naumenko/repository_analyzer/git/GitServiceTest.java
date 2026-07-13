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
    void clonesRepositoryOnceAndReusesExistingCopy() throws Exception {
        Path sourceDirectory = tempDirectory.resolve("source");
        Path clonesDirectory = tempDirectory.resolve("clones");
        createSourceRepository(sourceDirectory);

        DirectoryProperties properties = new DirectoryProperties(clonesDirectory);
        GitService gitService = new GitService(properties);
        String repositoryUrl = sourceDirectory.toUri().toString();

        Path firstClone = gitService.cloneRepository(repositoryUrl, "main");

        assertTrue(firstClone.startsWith(clonesDirectory.toAbsolutePath().normalize()));
        assertTrue(Files.isDirectory(firstClone.resolve(".git")));
        assertTrue(Files.isRegularFile(firstClone.resolve("README.md")));

        try (Git clonedGit = Git.open(firstClone.toFile())) {
            assertEquals("main", clonedGit.getRepository().getBranch());

            int commitCount = 0;
            for (RevCommit ignored : clonedGit.log().call()) {
                commitCount++;
            }
            assertEquals(1, commitCount);
        }

        Path marker = firstClone.resolve("clone-was-reused.marker");
        Files.writeString(marker, "created after the first clone");

        Path secondClone = gitService.cloneRepository(repositoryUrl, "main");

        assertEquals(firstClone, secondClone);
        assertTrue(Files.isRegularFile(marker));
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
}
