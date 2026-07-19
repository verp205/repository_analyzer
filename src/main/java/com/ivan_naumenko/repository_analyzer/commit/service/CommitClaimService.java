package com.ivan_naumenko.repository_analyzer.commit.service;

import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@AllArgsConstructor
@Service
public class CommitClaimService {

    private final RepositoryCommitRepository repositoryCommitRepository;

    public Optional<Integer> claimRandomCommit(Integer taskId) {
        while (true) {
            Optional<Integer> candidate = repositoryCommitRepository
                    .findRandomNewCommitId(taskId);

            if (candidate.isEmpty()) {
                return Optional.empty();
            }

            Integer commitId = candidate.get();

            int updatedRows = repositoryCommitRepository.claimIfNew(
                    commitId,
                    LocalDateTime.now()
            );

            if (updatedRows == 1) {
                return Optional.of(commitId);
            }
        }
    }
}
