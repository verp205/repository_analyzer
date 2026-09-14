package com.ivan_naumenko.repository_analyzer.git.service;

import com.ivan_naumenko.repository_analyzer.git.config.DirectoryProperties;
import com.ivan_naumenko.repository_analyzer.git.exception.BranchNotFoundException;
import com.ivan_naumenko.repository_analyzer.git.exception.RepositoryNotFoundException;
import com.ivan_naumenko.repository_analyzer.git.exception.RepositoryReadException;
import com.ivan_naumenko.repository_analyzer.git.exception.RepositorySyncException;
import com.ivan_naumenko.repository_analyzer.git.model.GitCommit;
import com.ivan_naumenko.repository_analyzer.git.model.GitCredentials;
import com.ivan_naumenko.repository_analyzer.git.model.GitTag;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.TagOpt;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
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

    public Path getRepositoryPath(String repositoryUrl, String branch) {
        validateArguments(repositoryUrl, branch);
        String repositoryKey = repositoryKey(
                repositoryUrl.trim(),
                branch.trim()
        );
        Path repositoryPath = getRepositoriesRoot().resolve(repositoryKey);

        if (!Files.isDirectory(repositoryPath.resolve(".git"))) {
            throw new RepositoryNotFoundException(
                    "Локальный репозиторий не найден: " + repositoryPath
            );
        }

        return repositoryPath;
    }

    public Path cloneOrUpdateRepository(
            String repositoryUrl,
            String branch
    ) throws RepositorySyncException {
        return cloneOrUpdateRepository(repositoryUrl, branch, null);
    }

    public Path cloneOrUpdateRepository(
            String repositoryUrl,
            String branch,
            GitCredentials credentials
    ) throws RepositorySyncException {
        validateArguments(repositoryUrl, branch);
        String normalizedRepositoryUrl = repositoryUrl.trim();
        String normalizedBranch = branch.trim();
        CredentialsProvider credentialsProvider = credentials == null
                ? null
                : credentials.createProvider();
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
                updateRepository(
                        repositoryDirectory,
                        normalizedBranch,
                        credentialsProvider
                );
            } else {
                removeIncompleteClone(repositoryDirectory);
                cloneRepository(
                        normalizedRepositoryUrl,
                        normalizedBranch,
                        repositoryDirectory,
                        credentialsProvider
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

    private void removeIncompleteClone(Path repositoryDirectory)
            throws IOException {
        if (!Files.exists(repositoryDirectory)) {
            return;
        }

        Files.walkFileTree(
                repositoryDirectory,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(
                            Path file,
                            BasicFileAttributes attributes
                    ) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(
                            Path directory,
                            IOException exception
                    ) throws IOException {
                        if (exception != null) {
                            throw exception;
                        }
                        Files.delete(directory);
                        return FileVisitResult.CONTINUE;
                    }
                }
        );
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

    /** Теги локальной копии, от новых к старым. Теги не на коммиты пропускаются. */
    public List<GitTag> listTags(String repositoryUrl, String branch) {
        validateArguments(repositoryUrl, branch);
        String repositoryKey = repositoryKey(repositoryUrl.trim(), branch.trim());
        Lock repositoryLock = lockFor(repositoryKey);
        repositoryLock.lock();

        try (Git git = openLocalRepository(repositoryKey);
             RevWalk revWalk = new RevWalk(git.getRepository())) {
            List<GitTag> tags = new ArrayList<>();
            for (Ref ref : git.getRepository()
                    .getRefDatabase()
                    .getRefsByPrefix(Constants.R_TAGS)) {
                try {
                    RevCommit commit = revWalk.parseCommit(ref.getObjectId());
                    tags.add(new GitTag(
                            ref.getName().substring(Constants.R_TAGS.length()),
                            commit.getName(),
                            Instant.ofEpochSecond(commit.getCommitTime())
                    ));
                } catch (IncorrectObjectTypeException | MissingObjectException ignored) {
                    // Тег указывает не на коммит — для периода отчёта он бесполезен.
                }
            }
            tags.sort(Comparator.comparing(GitTag::committedAt)
                    .reversed()
                    .thenComparing(GitTag::name));
            return tags;
        } catch (IOException exception) {
            throw new RepositoryReadException(
                    "Не удалось прочитать теги репозитория",
                    exception
            );
        } finally {
            repositoryLock.unlock();
        }
    }

    /**
     * Хэши коммитов, достижимых из toTag (или из головы ветки, если toTag
     * не задан), но не из fromTag — аналог {@code git log fromTag..toTag}.
     */
    public Set<String> commitsBetween(
            String repositoryUrl,
            String branch,
            String fromTag,
            String toTag
    ) {
        validateArguments(repositoryUrl, branch);
        if (fromTag == null || fromTag.isBlank()) {
            throw new IllegalArgumentException("Начальный тег не должен быть пустым");
        }
        String normalizedBranch = branch.trim();
        String repositoryKey = repositoryKey(repositoryUrl.trim(), normalizedBranch);
        Lock repositoryLock = lockFor(repositoryKey);
        repositoryLock.lock();

        try (Git git = openLocalRepository(repositoryKey);
             RevWalk revWalk = new RevWalk(git.getRepository())) {
            Repository repository = git.getRepository();
            ObjectId start = toTag == null
                    ? requireRef(
                            repository,
                            Constants.R_HEADS + normalizedBranch,
                            "Локальная ветка не найдена: " + normalizedBranch
                    )
                    : requireRef(
                            repository,
                            Constants.R_TAGS + toTag.strip(),
                            "Тег не найден: " + toTag.strip()
                    );
            ObjectId excluded = requireRef(
                    repository,
                    Constants.R_TAGS + fromTag.strip(),
                    "Тег не найден: " + fromTag.strip()
            );

            revWalk.markStart(revWalk.parseCommit(start));
            revWalk.markUninteresting(revWalk.parseCommit(excluded));

            Set<String> hashes = new HashSet<>();
            for (RevCommit commit : revWalk) {
                hashes.add(commit.getName());
            }
            return hashes;
        } catch (IOException exception) {
            throw new RepositoryReadException(
                    "Не удалось определить коммиты между тегами",
                    exception
            );
        } finally {
            repositoryLock.unlock();
        }
    }

    private Git openLocalRepository(String repositoryKey) throws IOException {
        Path localRepository = getRepositoriesRoot().resolve(repositoryKey);
        if (!Files.isDirectory(localRepository.resolve(".git"))) {
            throw new RepositoryNotFoundException(
                    "Локальный репозиторий не найден: " + localRepository
            );
        }
        return Git.open(localRepository.toFile());
    }

    private ObjectId requireRef(
            Repository repository,
            String name,
            String message
    ) throws IOException {
        Ref ref = repository.exactRef(name);
        if (ref == null || ref.getObjectId() == null) {
            throw new IllegalArgumentException(message);
        }
        return ref.getObjectId();
    }

    private void updateRepository(
            Path repositoryDirectory,
            String branch,
            CredentialsProvider credentialsProvider
    ) throws IOException, GitAPIException {
        try (Git git = Git.open(repositoryDirectory.toFile())) {
            FetchCommand fetchCommand = git.fetch()
                    .setRemote(REMOTE_NAME)
                    .setTagOpt(TagOpt.FETCH_TAGS);

            if (credentialsProvider != null) {
                fetchCommand.setCredentialsProvider(credentialsProvider);
            }

            fetchCommand.call();

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

    public String readFileAtCommit(
            Path repositoryPath,
            String commitHash,
            String filePath
    ) {
        validateFileReadArguments(repositoryPath, commitHash, filePath);

        String normalizedCommitHash = commitHash.trim();
        String normalizedFilePath = normalizeGitPath(filePath);

        try (Git git = Git.open(repositoryPath.toFile());
             RevWalk revWalk = new RevWalk(git.getRepository())) {
            Repository repository = git.getRepository();
            ObjectId commitId = repository.resolve(normalizedCommitHash);

            if (commitId == null
                    || !repository.getObjectDatabase().has(commitId)) {
                throw new RepositoryReadException(
                        "Коммит не найден: " + normalizedCommitHash
                );
            }

            RevCommit commit = revWalk.parseCommit(commitId);

            try (TreeWalk treeWalk = TreeWalk.forPath(
                    repository,
                    normalizedFilePath,
                    commit.getTree()
            )) {
                if (treeWalk == null) {
                    throw new RepositoryReadException(
                            "Файл "
                                    + normalizedFilePath
                                    + " не найден в коммите "
                                    + normalizedCommitHash
                    );
                }

                ObjectId blobId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(blobId);

                return new String(
                        loader.getBytes(),
                        StandardCharsets.UTF_8
                );
            }
        } catch (IOException exception) {
            throw new RepositoryReadException(
                    "Не удалось прочитать файл "
                            + normalizedFilePath
                            + " из коммита "
                            + normalizedCommitHash,
                    exception
            );
        }
    }

    private void cloneRepository(
            String repositoryUrl,
            String branch,
            Path repositoryDirectory,
            CredentialsProvider credentialsProvider
    ) throws GitAPIException {
        CloneCommand cloneCommand = Git.cloneRepository()
                .setURI(repositoryUrl)
                .setDirectory(repositoryDirectory.toFile())
                .setBranch(branch)
                .setTagOption(TagOpt.FETCH_TAGS);

        if (credentialsProvider != null) {
            cloneCommand.setCredentialsProvider(credentialsProvider);
        }

        try (Git ignored = cloneCommand.call()) {
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

    private void validateFileReadArguments(
            Path repositoryPath,
            String commitHash,
            String filePath
    ) {
        if (repositoryPath == null) {
            throw new IllegalArgumentException(
                    "Путь к репозиторию не должен быть null"
            );
        }

        if (commitHash == null || commitHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Хеш коммита не должен быть пустым"
            );
        }

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException(
                    "Путь к файлу не должен быть пустым"
            );
        }
    }

    private String normalizeGitPath(String filePath) {
        String normalizedPath = filePath.trim().replace('\\', '/');

        if (normalizedPath.startsWith("/")
                || Arrays.stream(normalizedPath.split("/"))
                .anyMatch(segment -> segment.equals(".."))) {
            throw new IllegalArgumentException(
                    "Путь внутри Git должен быть относительным: " + filePath
            );
        }

        return normalizedPath;
    }
}
