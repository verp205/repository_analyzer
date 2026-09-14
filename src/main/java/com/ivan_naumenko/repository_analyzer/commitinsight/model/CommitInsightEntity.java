package com.ivan_naumenko.repository_analyzer.commitinsight.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "commit_insight")
public class CommitInsightEntity {

    @Id
    @Column(name = "commit_id")
    private Integer commitId;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CommitInsightStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private ChangeCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    @Column(name = "summary", nullable = false)
    private String summary;

    @Column(name = "details_json", nullable = false)
    private String detailsJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
