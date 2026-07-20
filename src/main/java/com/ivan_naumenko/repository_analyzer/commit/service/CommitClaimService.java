package com.ivan_naumenko.repository_analyzer.commit.service;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class CommitClaimService {

    private final RepositoryCommitRepository repositoryCommitRepository;
    private final SqliteWriteCoordinator writeCoordinator;

    public Optional<Integer> claimRandomCommit(Integer taskId) {
        Objects.requireNonNull(taskId, "taskId не должен быть null");

        return writeCoordinator.execute(() -> claimUnderWriteLock(taskId));
    }

    private Optional<Integer> claimUnderWriteLock(Integer taskId) {
        while (true) {
            Optional<Integer> candidate = repositoryCommitRepository
                    .findRandomNewCommitId(taskId);

            if (candidate.isEmpty()) {
                return Optional.empty();
            }

            Integer commitId = candidate.get();

            int updatedRows = repositoryCommitRepository
                    .updateProcessingStatusIfCurrent(
                    commitId,
                    CommitProcessingStatus.NEW,
                    CommitProcessingStatus.PROCESSING,
                    LocalDateTime.now()
            );

            if (updatedRows == 1) {
                return Optional.of(commitId);
            }
        }
    }
}
