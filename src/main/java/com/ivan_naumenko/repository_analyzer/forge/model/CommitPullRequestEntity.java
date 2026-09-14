package com.ivan_naumenko.repository_analyzer.forge.model;

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
@Table(name = "commit_pull_request")
public class CommitPullRequestEntity {

    @Id
    @Column(name = "commit_id")
    private Integer commitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PullRequestLookupStatus status;

    @Column(name = "context_json")
    private String contextJson;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
