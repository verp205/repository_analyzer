package com.ivan_naumenko.repository_analyzer.git.service;

import com.ivan_naumenko.repository_analyzer.git.config.DirectoryProperties;
import com.ivan_naumenko.repository_analyzer.git.exception.BranchNotFoundException;
import com.ivan_naumenko.repository_analyzer.git.exception.RepositoryNotFoundException;
import com.ivan_naumenko.repository_analyzer.git.exception.RepositoryReadException;
import com.ivan_naumenko.repository_analyzer.git.exception.RepositorySyncException;
import com.ivan_naumenko.repository_analyzer.git.model.GitCommit;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Service
public class GitService {

    private static final String REMOTE_NAME = "origin";
    private static final int REPOSITORY_LOCK_COUNT = 64;

    private final DirectoryProperties directoryProperties;
    private final Lock[] repositoryLocks = IntStream
            .range(0, REPOSITORY_LOCK_COUNT)
            .mapToObj(ignored -> (Lock) new ReentrantLock())
            .toArray(Lock[]::new);

    public Path cloneOrUpdateRepository(
            String repositoryUrl,
            String branch
    ) throws RepositorySyncException {
        validateArguments(repositoryUrl, branch);
        String normalizedRepositoryUrl = repositoryUrl.trim();
        String normalizedBranch = branch.trim();
        String repositoryKey = repositoryKey(
                normalizedRepositoryUrl,
                normalizedBranch
        );
        Lock repositoryLock = lockFor(repositoryKey);

        Path repositoriesRoot = getRepositoriesRoot();
        repositoryLock.lock();

        try {
            Files.createDirectories(repositoriesRoot);

            Path repositoryDirectory = repositoriesRoot.resolve(
                    repositoryKey
            );

            if (Files.isDirectory(repositoryDirectory.resolve(".git"))) {
                updateRepository(repositoryDirectory, normalizedBranch);
            } else {
                cloneRepository(
                        normalizedRepositoryUrl,
                        normalizedBranch,
                        repositoryDirectory
                );
            }

            return repositoryDirectory;

        } catch (IOException | GitAPIException exception) {
            throw new RepositorySyncException(
                    "Не удалось клонировать или обновить репозиторий: "
                            + normalizedRepositoryUrl
                            + ", ветка: "
                            + normalizedBranch,
                    exception
            );
        } finally {
            repositoryLock.unlock();
        }
    }

    public List<GitCommit> readHistory(
            String repositoryUrl,
            String branch
    ) {
        validateArguments(repositoryUrl, branch);
        String normalizedRepositoryUrl = repositoryUrl.trim();
        String normalizedBranch = branch.trim();
        String repositoryKey = repositoryKey(
                normalizedRepositoryUrl,
                normalizedBranch
        );
        Lock repositoryLock = lockFor(repositoryKey);
        repositoryLock.lock();

        try {
            return readHistoryLocked(repositoryKey, normalizedBranch);
        } finally {
            repositoryLock.unlock();
        }
    }

    private List<GitCommit> readHistoryLocked(
            String repositoryKey,
            String branch
    ) {
        Path localRepository = getRepositoriesRoot().resolve(repositoryKey);

        if (!Files.isDirectory(localRepository.resolve(".git"))) {
            throw new RepositoryNotFoundException(
                    "Локальный репозиторий не найден: " + localRepository
            );
        }

        try (Git git = Git.open(localRepository.toFile())) {
            Ref localBranch = git.getRepository()
                    .findRef("refs/heads/" + branch);

            if (localBranch == null) {
                throw new BranchNotFoundException(
                        "Локальная ветка не найдена: " + branch
                );
            }

            Iterable<RevCommit> commits = git.log()
                    .add(localBranch.getObjectId())
                    .call();

            List<GitCommit> gitCommits = new ArrayList<>();

            for (RevCommit commit : commits) {
                gitCommits.add(convertCommit(commit));
            }

            return gitCommits;

        } catch (IOException exception) {
            throw new RepositoryNotFoundException(
                    "Не удалось открыть локальный репозиторий: "
                            + localRepository,
                    exception
            );

        } catch (GitAPIException exception) {
            throw new RepositoryReadException(
                    "Не удалось прочитать историю ветки: " + branch,
                    exception
            );
        }
    }

    private void updateRepository(
            Path repositoryDirectory,
            String branch
    ) throws IOException, GitAPIException {
        try (Git git = Git.open(repositoryDirectory.toFile())) {
            git.fetch()
                    .setRemote(REMOTE_NAME)
                    .call();

            String remoteBranchName =
                    "refs/remotes/" + REMOTE_NAME + "/" + branch;

            Ref remoteBranch = git.getRepository()
                    .findRef(remoteBranchName);

            if (remoteBranch == null) {
                throw new BranchNotFoundException(
                        "Удалённая ветка не найдена: "
                                + REMOTE_NAME
                                + "/"
                                + branch
                );
            }

            String localBranchName = "refs/heads/" + branch;

            Ref localBranch = git.getRepository()
                    .findRef(localBranchName);

            if (localBranch == null) {
                git.checkout()
                        .setCreateBranch(true)
                        .setName(branch)
                        .setStartPoint(remoteBranchName)
                        .setUpstreamMode(
                                CreateBranchCommand.SetupUpstreamMode.TRACK
                        )
                        .call();
            } else {
                git.checkout()
                        .setName(branch)
                        .call();
            }

            git.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef(remoteBranchName)
                    .call();
        }
    }

    private void cloneRepository(
            String repositoryUrl,
            String branch,
            Path repositoryDirectory
    ) throws GitAPIException {
        try (Git ignored = Git.cloneRepository()
                .setURI(repositoryUrl)
                .setDirectory(repositoryDirectory.toFile())
                .setBranch(branch)
                .call()) {
        }
    }

    private GitCommit convertCommit(RevCommit commit) {
        PersonIdent author = commit.getAuthorIdent();

        List<String> parentHashes = Arrays.stream(commit.getParents())
                .map(RevCommit::getName)
                .toList();

        Instant commitTime = Instant.ofEpochSecond(
                commit.getCommitTime()
        );

        return new GitCommit(
                commit.getName(),
                parentHashes,
                author.getName(),
                author.getEmailAddress(),
                commitTime,
                commit.getFullMessage()
        );
    }

    private Path getRepositoriesRoot() {
        return directoryProperties.path()
                .toAbsolutePath()
                .normalize();
    }

    private Lock lockFor(String repositoryKey) {
        int lockIndex = Math.floorMod(
                repositoryKey.hashCode(),
                repositoryLocks.length
        );
        return repositoryLocks[lockIndex];
    }

    private String repositoryKey(
            String repositoryUrl,
            String branch
    ) {
        String source = repositoryUrl.trim()
                + "\n"
                + branch.trim();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    source.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Алгоритм SHA-256 недоступен",
                    exception
            );
        }
    }

    private void validateArguments(
            String repositoryUrl,
            String branch
    ) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "URL репозитория не должен быть пустым"
            );
        }

        if (branch == null || branch.isBlank()) {
            throw new IllegalArgumentException(
                    "Название ветки не должно быть пустым"
            );
        }
    }
}
