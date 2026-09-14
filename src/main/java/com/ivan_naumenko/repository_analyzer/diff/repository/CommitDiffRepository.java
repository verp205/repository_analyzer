package com.ivan_naumenko.repository_analyzer.diff.repository;

import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileRow;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiffEntity;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitStatsRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommitDiffRepository extends JpaRepository<CommitDiffEntity, Integer> {

    List<CommitDiffEntity> findAllByCommit_AnalysisTask_Id(
            Integer analysisTaskId
    );

    @Query("""
            SELECT new com.ivan_naumenko.repository_analyzer.diff.model.CommitStatsRow(
                diff.commitId,
                commit.commitHash,
                commit.message,
                commit.authorName,
                commit.authorEmail,
                commit.committedAt,
                diff.totalAddedLines,
                diff.totalDeletedLines
            )
            FROM CommitDiffEntity diff
            JOIN diff.commit commit
            WHERE commit.analysisTask.id = :taskId
            ORDER BY commit.committedAt ASC, commit.id ASC
            """)
    List<CommitStatsRow> findStatsByTask(@Param("taskId") Integer taskId);

    @Query("""
            SELECT new com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileRow(
                changed.commitDiff.commitId,
                changed.oldPath,
                changed.newPath,
                changed.changeType,
                changed.addedLines,
                changed.deletedLines
            )
            FROM ChangedFileEntity changed
            WHERE changed.commitDiff.commit.analysisTask.id = :taskId
            ORDER BY changed.commitDiff.commitId ASC, changed.filePosition ASC
            """)
    List<ChangedFileRow> findChangedFilesByTask(
            @Param("taskId") Integer taskId
    );
}
