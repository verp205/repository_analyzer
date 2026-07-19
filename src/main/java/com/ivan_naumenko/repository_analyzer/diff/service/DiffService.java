package com.ivan_naumenko.repository_analyzer.diff.service;

import com.ivan_naumenko.repository_analyzer.diff.exception.DiffReadException;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFile;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiff;
import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DiffService {

    public CommitDiff readDiff(Path repositoryPath, String commitHash) {
        validateArguments(repositoryPath, commitHash);
        Path normalizedPath = repositoryPath.toAbsolutePath().normalize();

        if (!Files.isDirectory(normalizedPath.resolve(".git"))) {
            throw new DiffReadException(
                    "Локальный Git-репозиторий не найден: " + normalizedPath
            );
        }

        try (Git git = Git.open(normalizedPath.toFile())) {
            return readDiff(git.getRepository(), commitHash.trim());
        } catch (IOException exception) {
            throw new DiffReadException(
                    "Не удалось открыть локальный репозиторий: "
                            + normalizedPath,
                    exception
            );
        }
    }

    private CommitDiff readDiff(
            Repository repository,
            String commitHash
    ) {
        try (RevWalk revWalk = new RevWalk(repository);
             ObjectReader reader = repository.newObjectReader()) {
            RevCommit commit = resolveCommit(repository, revWalk, commitHash);
            RevCommit parent = firstParent(revWalk, commit);
            AbstractTreeIterator oldTree = createOldTree(reader, parent);
            CanonicalTreeParser newTree = createTreeParser(
                    reader,
                    commit
            );

            return formatDiff(
                    repository,
                    commit,
                    parent,
                    oldTree,
                    newTree
            );
        } catch (IOException exception) {
            throw new DiffReadException(
                    "Не удалось прочитать diff коммита " + commitHash,
                    exception
            );
        }
    }

    private RevCommit resolveCommit(
            Repository repository,
            RevWalk revWalk,
            String commitHash
    ) throws IOException {
        var objectId = repository.resolve(commitHash);

        if (objectId == null) {
            throw new DiffReadException("Коммит не найден: " + commitHash);
        }

        try {
            return revWalk.parseCommit(objectId);
        } catch (IOException exception) {
            throw new DiffReadException(
                    "Объект не является Git-коммитом: " + commitHash,
                    exception
            );
        }
    }

    private RevCommit firstParent(
            RevWalk revWalk,
            RevCommit commit
    ) throws IOException {
        if (commit.getParentCount() == 0) {
            return null;
        }

        return revWalk.parseCommit(commit.getParent(0).getId());
    }

    private AbstractTreeIterator createOldTree(
            ObjectReader reader,
            RevCommit parent
    ) throws IOException {
        if (parent == null) {
            return new EmptyTreeIterator();
        }

        return createTreeParser(reader, parent);
    }

    private CanonicalTreeParser createTreeParser(
            ObjectReader reader,
            RevCommit commit
    ) throws IOException {
        CanonicalTreeParser parser = new CanonicalTreeParser();
        parser.reset(reader, commit.getTree().getId());
        return parser;
    }

    private CommitDiff formatDiff(
            Repository repository,
            RevCommit commit,
            RevCommit parent,
            AbstractTreeIterator oldTree,
            AbstractTreeIterator newTree
    ) throws IOException {
        List<DiffEntry> entries;

        try (DiffFormatter scanner = new DiffFormatter(
                DisabledOutputStream.INSTANCE
        )) {
            scanner.setRepository(repository);
            scanner.setDetectRenames(true);
            entries = scanner.scan(oldTree, newTree);
        }

        List<ChangedFile> changedFiles = new ArrayList<>(entries.size());
        DiffOutputStream rawDiff = new DiffOutputStream();

        try (DiffFormatter formatter = new DiffFormatter(rawDiff)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);

            for (DiffEntry entry : entries) {
                int patchStart = rawDiff.size();
                FileHeader header = formatter.toFileHeader(entry);
                formatter.format(entry);
                int patchEnd = rawDiff.size();

                changedFiles.add(toChangedFile(
                        entry,
                        header,
                        rawDiff,
                        patchStart,
                        patchEnd
                ));
            }
        }

        int totalAddedLines = changedFiles.stream()
                .mapToInt(ChangedFile::addedLines)
                .sum();
        int totalDeletedLines = changedFiles.stream()
                .mapToInt(ChangedFile::deletedLines)
                .sum();

        return new CommitDiff(
                commit.getName(),
                parent == null ? null : parent.getName(),
                changedFiles,
                totalAddedLines,
                totalDeletedLines,
                rawDiff.toString(StandardCharsets.UTF_8)
        );
    }

    private ChangedFile toChangedFile(
            DiffEntry entry,
            FileHeader header,
            DiffOutputStream rawDiff,
            int patchStart,
            int patchEnd
    ) {
        int addedLines = 0;
        int deletedLines = 0;

        for (Edit edit : header.toEditList()) {
            addedLines += edit.getEndB() - edit.getBeginB();
            deletedLines += edit.getEndA() - edit.getBeginA();
        }

        String oldPath = normalizePath(entry.getOldPath());
        String newPath = normalizePath(entry.getNewPath());
        String effectivePath = newPath != null ? newPath : oldPath;
        String patch = rawDiff.utf8(patchStart, patchEnd);

        return new ChangedFile(
                oldPath,
                newPath,
                mapChangeType(entry.getChangeType()),
                addedLines,
                deletedLines,
                effectivePath.endsWith(".java"),
                patch
        );
    }

    private String normalizePath(String path) {
        return DiffEntry.DEV_NULL.equals(path) ? null : path;
    }

    private FileChangeType mapChangeType(DiffEntry.ChangeType changeType) {
        return switch (changeType) {
            case ADD -> FileChangeType.ADD;
            case MODIFY -> FileChangeType.MODIFY;
            case DELETE -> FileChangeType.DELETE;
            case RENAME -> FileChangeType.RENAME;
            case COPY -> FileChangeType.COPY;
        };
    }

    private void validateArguments(Path repositoryPath, String commitHash) {
        Objects.requireNonNull(
                repositoryPath,
                "repositoryPath не должен быть null"
        );

        if (commitHash == null || commitHash.isBlank()) {
            throw new IllegalArgumentException(
                    "commitHash не должен быть пустым"
            );
        }
    }

    private static final class DiffOutputStream
            extends ByteArrayOutputStream {

        private String utf8(int start, int end) {
            return new String(
                    buf,
                    start,
                    end - start,
                    StandardCharsets.UTF_8
            );
        }
    }
}
