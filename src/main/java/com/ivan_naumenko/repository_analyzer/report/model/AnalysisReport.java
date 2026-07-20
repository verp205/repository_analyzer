package com.ivan_naumenko.repository_analyzer.report.model;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "analysis_report",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_analysis_report_task_audience",
                columnNames = {"analysis_task_id", "audience"}
        )
)
public class AnalysisReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_task_id", nullable = false)
    private AnalysisTask analysisTask;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false)
    private ReportAudience audience;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status;

    @Column(name = "total_commits", nullable = false)
    private int totalCommits;

    @Column(name = "processed_commits", nullable = false)
    private int processedCommits;

    @Column(name = "content")
    private String content;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
