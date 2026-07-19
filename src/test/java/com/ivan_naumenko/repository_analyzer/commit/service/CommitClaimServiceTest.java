package com.ivan_naumenko.repository_analyzer.commit.service;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommitClaimServiceTest {

    private static final Integer TASK_ID = 10;

    @Mock
    private RepositoryCommitRepository repository;

    @Test
    void retriesWhenAnotherWorkerClaimsFirstCandidate() {
        CommitClaimService service = new CommitClaimService(repository);
        when(repository.findRandomNewCommitId(TASK_ID))
                .thenReturn(Optional.of(101), Optional.of(102));
        when(repository.updateProcessingStatusIfCurrent(
                any(),
                any(),
                any(),
                any(LocalDateTime.class)
        )).thenReturn(0, 1);

        Optional<Integer> claimed = service.claimRandomCommit(TASK_ID);

        assertEquals(Optional.of(102), claimed);
        verify(repository, times(2)).findRandomNewCommitId(TASK_ID);
        verify(repository).updateProcessingStatusIfCurrent(
                eq(101),
                eq(CommitProcessingStatus.NEW),
                eq(CommitProcessingStatus.PROCESSING),
                any(LocalDateTime.class)
        );
        verify(repository).updateProcessingStatusIfCurrent(
                eq(102),
                eq(CommitProcessingStatus.NEW),
                eq(CommitProcessingStatus.PROCESSING),
                any(LocalDateTime.class)
        );
    }

    @Test
    void returnsEmptyWhenNoNewCommitExists() {
        CommitClaimService service = new CommitClaimService(repository);
        when(repository.findRandomNewCommitId(TASK_ID))
                .thenReturn(Optional.empty());

        Optional<Integer> claimed = service.claimRandomCommit(TASK_ID);

        assertTrue(claimed.isEmpty());
    }
}
