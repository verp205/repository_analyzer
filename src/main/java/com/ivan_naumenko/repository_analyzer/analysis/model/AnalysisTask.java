package com.ivan_naumenko.repository_analyzer.analysis.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "analysis_task")
public class AnalysisTask {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "repository_url", nullable = false)
    private String repositoryUrl;

    @Column(name = "branch", nullable = false)
    private String branch;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AnalysisTaskStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
