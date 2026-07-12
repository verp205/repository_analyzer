package com.ivan_naumenko.repository_analyzer.api.dto;

import com.ivan_naumenko.repository_analyzer.model.AnalysisTaskStatus;
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
}
