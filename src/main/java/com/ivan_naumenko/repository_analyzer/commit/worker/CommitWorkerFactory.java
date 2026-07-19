package com.ivan_naumenko.repository_analyzer.commit.worker;

import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisCompletionService;
import com.ivan_naumenko.repository_analyzer.commit.service.CommitClaimService;
import com.ivan_naumenko.repository_analyzer.commit.service.CommitProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CommitWorkerFactory {
    private final CommitClaimService commitClaimService;
    private final CommitProcessor commitProcessor;
    private final AnalysisCompletionService analysisCompletionService;

    public Runnable create(Integer taskId) {
        Objects.requireNonNull(taskId, "taskId не должен быть null");

        return () -> {
            while (!Thread.currentThread().isInterrupted()) {
                Optional<Integer> candidate = commitClaimService
                        .claimRandomCommit(taskId);

                if (candidate.isEmpty()) {
                    analysisCompletionService.finishIfNoWorkRemaining(taskId);
                    return;
                }

                commitProcessor.process(candidate.get());
            }
        };
    }
}
