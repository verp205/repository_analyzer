package com.ivan_naumenko.repository_analyzer.commit.worker;

import com.ivan_naumenko.repository_analyzer.commit.service.CommitClaimService;
import com.ivan_naumenko.repository_analyzer.commit.service.CommitProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommitWorkerFactoryTest {

    private static final Integer TASK_ID = 10;

    @Mock
    private CommitClaimService commitClaimService;

    @Mock
    private CommitProcessor commitProcessor;

    @Test
    void createsLazyWorkerThatProcessesCommitsUntilQueueIsEmpty() {
        CommitWorkerFactory factory = new CommitWorkerFactory(
                commitClaimService,
                commitProcessor
        );
        when(commitClaimService.claimRandomCommit(TASK_ID)).thenReturn(
                Optional.of(101),
                Optional.of(102),
                Optional.empty()
        );

        Runnable worker = factory.create(TASK_ID);

        verifyNoInteractions(commitClaimService, commitProcessor);

        worker.run();

        InOrder order = inOrder(commitClaimService, commitProcessor);
        order.verify(commitClaimService).claimRandomCommit(TASK_ID);
        order.verify(commitProcessor).process(101);
        order.verify(commitClaimService).claimRandomCommit(TASK_ID);
        order.verify(commitProcessor).process(102);
        order.verify(commitClaimService).claimRandomCommit(TASK_ID);
        order.verifyNoMoreInteractions();
    }
}
