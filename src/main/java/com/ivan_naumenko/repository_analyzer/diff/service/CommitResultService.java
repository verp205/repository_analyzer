package com.ivan_naumenko.repository_analyzer.diff.service;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileEntity;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiffEntity;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiff;
import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;
import com.ivan_naumenko.repository_analyzer.diff.repository.CommitDiffRepository;
import com.ivan_naumenko.repository_analyzer.javaanalysis.entity.JavaFileAnalysisEntity;
import com.ivan_naumenko.repository_analyzer.javaanalysis.mapper.JavaAnalysisMapper;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.ChangedJavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.repository.JavaFileAnalysisRepository;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommitResultService {

    private static final int MAX_ERROR_LENGTH = 2_000;

    private final RepositoryCommitRepository commitRepository;
    private final CommitDiffRepository diffRepository;
    private final JavaAnalysisMapper javaAnalysisMapper;
    private final JavaFileAnalysisRepository javaAnalysisRepository;
    private final SqliteWriteCoordinator writeCoordinator;
    private final TransactionTemplate transactionTemplate;

    public void complete(
            Integer commitId,
            CommitDiff diff,
            List<ChangedJavaFileAnalysis> javaAnalyses
    ) {
        Objects.requireNonNull(commitId, "commitId не должен быть null");
        Objects.requireNonNull(diff, "diff не должен быть null");
        Objects.requireNonNull(
                javaAnalyses,
                "javaAnalyses не должен быть null"
        );

        writeCoordinator.execute(() -> transactionTemplate.executeWithoutResult(
                ignored -> completeInTransaction(commitId, diff, javaAnalyses)
        ));
    }

    private void completeInTransaction(
            Integer commitId,
            CommitDiff diff,
            List<ChangedJavaFileAnalysis> javaAnalyses
    ) {
        RepositoryCommit commit = commitRepository.getReferenceById(commitId);
        if (!commit.getCommitHash().equals(diff.commitHash())) {
            throw new IllegalArgumentException(
                    "Хеш результата diff не совпадает с хешем коммита"
            );
        }

        CommitDiffEntity entity = toEntity(commit, diff);
        diffRepository.saveAndFlush(entity);
        saveJavaAnalyses(entity, javaAnalyses);

        int updatedRows = commitRepository.finishProcessingIfCurrent(
                commitId,
                CommitProcessingStatus.PROCESSING,
                CommitProcessingStatus.COMPLETED,
                null,
                LocalDateTime.now()
        );

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Коммит " + commitId + " больше не находится в PROCESSING"
            );
        }
    }

    private void saveJavaAnalyses(
            CommitDiffEntity diff,
            List<ChangedJavaFileAnalysis> javaAnalyses
    ) {
        Set<Integer> analyzedPositions = new HashSet<>();
        List<JavaFileAnalysisEntity> entities = javaAnalyses.stream()
                .map(javaAnalysis -> {
                    int position = javaAnalysis.filePosition();
                    if (position >= diff.getChangedFiles().size()) {
                        throw new IllegalArgumentException(
                                "Позиция Java-анализа выходит за границы diff: "
                                        + position
                        );
                    }
                    if (!analyzedPositions.add(position)) {
                        throw new IllegalArgumentException(
                                "Java-файл с позицией "
                                        + position
                                        + " проанализирован повторно"
                        );
                    }

                    ChangedFileEntity changedFile = diff.getChangedFiles()
                            .get(position);
                    if (!requiresJavaAnalysis(changedFile)) {
                        throw new IllegalArgumentException(
                                "Файл с позицией "
                                        + position
                                        + " не требует Java-анализа"
                        );
                    }

                    return javaAnalysisMapper.toEntity(
                            changedFile,
                            javaAnalysis.analysis()
                    );
                })
                .toList();

        for (int position = 0;
             position < diff.getChangedFiles().size();
             position++) {
            ChangedFileEntity changedFile = diff.getChangedFiles()
                    .get(position);
            if (requiresJavaAnalysis(changedFile)
                    && !analyzedPositions.contains(position)) {
                throw new IllegalArgumentException(
                        "Отсутствует Java-анализ файла с позицией "
                                + position
                );
            }
        }

        javaAnalysisRepository.saveAll(entities);
    }

    private boolean requiresJavaAnalysis(ChangedFileEntity changedFile) {
        return changedFile.isJavaFile()
                && changedFile.getChangeType() != FileChangeType.DELETE
                && changedFile.getNewPath() != null;
    }

    public boolean fail(Integer commitId, Throwable cause) {
        Objects.requireNonNull(commitId, "commitId не должен быть null");
        Objects.requireNonNull(cause, "cause не должен быть null");

        return writeCoordinator.execute(() -> transactionTemplate.execute(
                ignored -> commitRepository.finishProcessingIfCurrent(
                        commitId,
                        CommitProcessingStatus.PROCESSING,
                        CommitProcessingStatus.FAILED,
                        errorMessage(cause),
                        LocalDateTime.now()
                ) == 1
        ));
    }

    private CommitDiffEntity toEntity(
            RepositoryCommit commit,
            CommitDiff diff
    ) {
        CommitDiffEntity entity = new CommitDiffEntity();
        entity.setCommit(commit);
        entity.setParentHash(diff.parentHash());
        entity.setTotalAddedLines(diff.totalAddedLines());
        entity.setTotalDeletedLines(diff.totalDeletedLines());
        entity.setRawDiff(diff.rawDiff());
        entity.setCreatedAt(LocalDateTime.now());

        for (int position = 0; position < diff.changedFiles().size(); position++) {
            var changedFile = diff.changedFiles().get(position);
            ChangedFileEntity fileEntity = new ChangedFileEntity();
            fileEntity.setFilePosition(position);
            fileEntity.setOldPath(changedFile.oldPath());
            fileEntity.setNewPath(changedFile.newPath());
            fileEntity.setChangeType(changedFile.changeType());
            fileEntity.setAddedLines(changedFile.addedLines());
            fileEntity.setDeletedLines(changedFile.deletedLines());
            fileEntity.setJavaFile(changedFile.javaFile());
            fileEntity.setPatch(changedFile.patch());
            entity.addChangedFile(fileEntity);
        }

        return entity;
    }

    private String errorMessage(Throwable cause) {
        String message = cause.getClass().getSimpleName()
                + ": "
                + Objects.requireNonNullElse(cause.getMessage(), "без сообщения");
        if (message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}
