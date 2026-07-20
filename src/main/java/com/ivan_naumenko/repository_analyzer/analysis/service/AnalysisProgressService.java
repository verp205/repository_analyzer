package com.ivan_naumenko.repository_analyzer.analysis.service;

import com.ivan_naumenko.repository_analyzer.analysis.dto.AnalysisTaskProgressResponse;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitStatusCount;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalysisProgressService {

    private final AnalysisTaskService taskService;
    private final RepositoryCommitRepository commitRepository;

    @Transactional(readOnly = true)
    public AnalysisTaskProgressResponse get(Integer taskId) {
        AnalysisTask task = taskService.getById(taskId);
        Map<CommitProcessingStatus, Long> counts = new EnumMap<>(
                CommitProcessingStatus.class
        );
        for (CommitStatusCount count : commitRepository.countByStatus(taskId)) {
            counts.put(count.status(), count.count());
        }

        long newCommits = count(counts, CommitProcessingStatus.NEW);
        long processing = count(counts, CommitProcessingStatus.PROCESSING);
        long completed = count(counts, CommitProcessingStatus.COMPLETED);
        long failed = count(counts, CommitProcessingStatus.FAILED);

        return new AnalysisTaskProgressResponse(
                taskId,
                task.getStatus(),
                newCommits + processing + completed + failed,
                newCommits,
                processing,
                completed,
                failed
        );
    }

    private long count(
            Map<CommitProcessingStatus, Long> counts,
            CommitProcessingStatus status
    ) {
        return counts.getOrDefault(status, 0L);
    }
}
