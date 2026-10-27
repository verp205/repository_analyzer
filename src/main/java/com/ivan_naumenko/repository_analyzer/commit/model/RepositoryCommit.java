package com.ivan_naumenko.repository_analyzer.commit.model;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "repository_commit",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_repository_commit_task_hash",
                columnNames = {"analysis_task_id", "commit_hash"}
        )
)
public class RepositoryCommit {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_task_id", nullable = false)
    private AnalysisTask analysisTask;

    @Column(name = "commit_hash", nullable = false)
    private String commitHash;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "committed_at", nullable = false)
    private Instant committedAt;

    @Column(name = "message")
    private String message;

    @Column(name = "processing_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private CommitProcessingStatus processingStatus;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "reachable", nullable = false)
    private boolean reachable = true;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
