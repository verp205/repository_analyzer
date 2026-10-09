package com.ivan_naumenko.repository_analyzer.analysis.dto;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AnalysisTaskResponse {

    private Integer id;

    private String repositoryUrl;

    private String branch;

    private AnalysisTaskStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private int revision;
    private String headCommitHash;
    private int addedCommits;

    public AnalysisTaskResponse(Integer id, String repositoryUrl, String branch,
                                AnalysisTaskStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, repositoryUrl, branch, status, createdAt, updatedAt, 0, null, 0);
    }
}
