package com.ivan_naumenko.repository_analyzer.diff.model;

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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "commit_changed_file",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_changed_file_commit_position",
                columnNames = {"commit_id", "file_position"}
        )
)
public class ChangedFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commit_id", nullable = false)
    private CommitDiffEntity commitDiff;

    @Column(name = "file_position", nullable = false)
    private int filePosition;

    @Column(name = "old_path")
    private String oldPath;

    @Column(name = "new_path")
    private String newPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private FileChangeType changeType;

    @Column(name = "added_lines", nullable = false)
    private int addedLines;

    @Column(name = "deleted_lines", nullable = false)
    private int deletedLines;

    @Column(name = "java_file", nullable = false)
    private boolean javaFile;

    @Column(name = "patch", nullable = false)
    private String patch;
}
