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
                name = "uk_analysis_report_task_audience_period_revision",
                columnNames = {"analysis_task_id", "audience", "period_key", "analysis_revision"}
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
    @Column(name = "period_type", nullable = false)
    private ReportPeriodType periodType = ReportPeriodType.ALL;

    @Column(name = "period_days")
    private Integer periodDays;

    @Column(name = "period_from")
    private String periodFrom;

    @Column(name = "period_to")
    private String periodTo;

    @Column(name = "period_key", nullable = false)
    private String periodKey = "ALL";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status;

    @Column(name = "total_commits", nullable = false)
    private int totalCommits;

    @Column(name = "processed_commits", nullable = false)
    private int processedCommits;

    @Column(name = "content")
    private String content;

    @Column(name = "analysis_revision", nullable = false)
    private int analysisRevision;

    @Column(name = "head_commit_hash")
    private String headCommitHash;

    @Column(name = "commit_ids_json")
    private String commitIdsJson;

    @Column(name = "snapshot_json")
    private String snapshotJson;

    @Column(name = "facts_json")
    private String factsJson;

    @Column(name = "artifacts_json")
    private String artifactsJson;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ReportPeriod getPeriod() {
        return new ReportPeriod(periodType, periodDays, periodFrom, periodTo);
    }

    public void setPeriod(ReportPeriod period) {
        ReportPeriod value = period == null ? ReportPeriod.all() : period;
        periodType = value.type();
        periodDays = value.days();
        periodFrom = value.fromTag();
        periodTo = value.toTag();
        periodKey = value.key();
    }
}
