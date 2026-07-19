package com.ivan_naumenko.repository_analyzer.diff.service;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileEntity;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiffEntity;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiff;
import com.ivan_naumenko.repository_analyzer.diff.repository.CommitDiffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CommitResultService {

    private static final int MAX_ERROR_LENGTH = 2_000;

    private final RepositoryCommitRepository commitRepository;
    private final CommitDiffRepository diffRepository;

    @Transactional
    public void complete(Integer commitId, CommitDiff diff) {
        Objects.requireNonNull(commitId, "commitId не должен быть null");
        Objects.requireNonNull(diff, "diff не должен быть null");

        RepositoryCommit commit = commitRepository.getReferenceById(commitId);
        if (!commit.getCommitHash().equals(diff.commitHash())) {
            throw new IllegalArgumentException(
                    "Хеш результата diff не совпадает с хешем коммита"
            );
        }

        CommitDiffEntity entity = toEntity(commit, diff);
        diffRepository.save(entity);

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

    @Transactional
    public boolean fail(Integer commitId, Throwable cause) {
        Objects.requireNonNull(commitId, "commitId не должен быть null");
        Objects.requireNonNull(cause, "cause не должен быть null");

        return commitRepository.finishProcessingIfCurrent(
                commitId,
                CommitProcessingStatus.PROCESSING,
                CommitProcessingStatus.FAILED,
                errorMessage(cause),
                LocalDateTime.now()
        ) == 1;
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
