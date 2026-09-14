package com.ivan_naumenko.repository_analyzer.git;

import com.ivan_naumenko.repository_analyzer.git.config.DirectoryProperties;
import com.ivan_naumenko.repository_analyzer.git.model.GitTag;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitServiceTagsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void listsTagsAndResolvesCommitRanges() throws Exception {
        Path source = tempDirectory.resolve("source");
        RevCommit first;
        RevCommit second;
        RevCommit third;
        RevCommit fourth;

        try (Git git = Git.init()
                .setDirectory(source.toFile())
                .setInitialBranch("main")
                .call()) {
            first = commit(git, source, "a.txt", "1", "First", 1);
            git.tag().setName("v1.0").setObjectId(first).setAnnotated(true).setMessage("Release 1.0").call();
            second = commit(git, source, "a.txt", "2", "Second", 2);
            third = commit(git, source, "b.txt", "3", "Third", 3);
            git.tag().setName("v1.1").setObjectId(third).setAnnotated(false).call();
            fourth = commit(git, source, "c.txt", "4", "Fourth", 4);
        }

        GitService gitService = new GitService(new DirectoryProperties(tempDirectory.resolve("clones")));
        String url = source.toUri().toString();
        gitService.cloneOrUpdateRepository(url, "main");

        List<GitTag> tags = gitService.listTags(url, "main");
        assertEquals(List.of("v1.1", "v1.0"), tags.stream().map(GitTag::name).toList());
        assertEquals(third.getName(), tags.getFirst().commitHash());

        assertEquals(
                Set.of(second.getName(), third.getName(), fourth.getName()),
                gitService.commitsBetween(url, "main", "v1.0", null)
        );
        assertEquals(
                Set.of(second.getName(), third.getName()),
                gitService.commitsBetween(url, "main", "v1.0", "v1.1")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> gitService.commitsBetween(url, "main", "missing", null)
        );

        try (Git git = Git.open(source.toFile())) {
            git.tag().setName("v1.2").setObjectId(fourth).setAnnotated(false).call();
        }
        gitService.cloneOrUpdateRepository(url, "main");

        assertEquals(
                List.of("v1.2", "v1.1", "v1.0"),
                gitService.listTags(url, "main").stream().map(GitTag::name).toList()
        );
    }

    private RevCommit commit(
            Git git,
            Path directory,
            String file,
            String content,
            String message,
            int hour
    ) throws Exception {
        Files.writeString(directory.resolve(file), content);
        git.add().addFilepattern(file).call();
        PersonIdent author = new PersonIdent(
                "Author",
                "author@example.com",
                Instant.parse("2026-01-01T00:00:00Z").plusSeconds(hour * 3_600L),
                ZoneOffset.UTC
        );
        return git.commit()
                .setMessage(message)
                .setAuthor(author)
                .setCommitter(author)
                .call();
    }
}
