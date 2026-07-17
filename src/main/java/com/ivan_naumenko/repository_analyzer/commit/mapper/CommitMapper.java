package com.ivan_naumenko.repository_analyzer.commit.mapper;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import com.ivan_naumenko.repository_analyzer.git.model.GitCommit;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CommitMapper {

    public RepositoryCommit toEntity(
            AnalysisTask task,
            GitCommit gitCommit,
            LocalDateTime now
    ) {
        RepositoryCommit commit = new RepositoryCommit();
        commit.setAnalysisTask(task);
        commit.setCommitHash(gitCommit.commitHash());
        commit.setAuthorName(gitCommit.authorName());
        commit.setAuthorEmail(gitCommit.authorMail());
        commit.setCommittedAt(gitCommit.committedAt());
        commit.setMessage(gitCommit.message());
        commit.setProcessingStatus(CommitProcessingStatus.NEW);
        commit.setAttemptCount(0);
        commit.setCreatedAt(now);
        commit.setUpdatedAt(now);
        return commit;
    }
}
