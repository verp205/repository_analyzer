package com.ivan_naumenko.repository_analyzer.diff.model;

import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "commit_diff")
public class CommitDiffEntity {

    @Id
    @Column(name = "commit_id")
    private Integer commitId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commit_id")
    private RepositoryCommit commit;

    @Column(name = "parent_hash")
    private String parentHash;

    @Column(name = "total_added_lines", nullable = false)
    private int totalAddedLines;

    @Column(name = "total_deleted_lines", nullable = false)
    private int totalDeletedLines;

    @Column(name = "raw_diff", nullable = false)
    private String rawDiff;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OrderBy("filePosition ASC")
    @OneToMany(
            mappedBy = "commitDiff",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ChangedFileEntity> changedFiles = new ArrayList<>();

    public void addChangedFile(ChangedFileEntity changedFile) {
        changedFiles.add(changedFile);
        changedFile.setCommitDiff(this);
    }
}
