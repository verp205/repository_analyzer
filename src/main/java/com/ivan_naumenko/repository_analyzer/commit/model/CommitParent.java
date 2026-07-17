package com.ivan_naumenko.repository_analyzer.commit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "commit_parent",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_commit_parent_child_position",
                columnNames = {"child_commit_id", "parent_position"}
        )
)
public class CommitParent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_commit_id", nullable = false)
    private RepositoryCommit childCommit;

    @Column(name = "parent_hash", nullable = false)
    private String parentHash;

    @Column(name = "parent_position", nullable = false)
    private Integer parentPosition;
}
