package com.ivan_naumenko.repository_analyzer.diff.repository;

import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileEntity;
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

    @Query("SELECT DISTINCT d FROM CommitDiffEntity d JOIN FETCH d.commit c JOIN FETCH c.analysisTask LEFT JOIN FETCH d.changedFiles WHERE d.commitId IN :ids")
    List<CommitDiffEntity> findForContext(@Param("ids") List<Integer> ids);

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
              AND commit.reachable = true
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
              AND changed.commitDiff.commit.reachable = true
            ORDER BY changed.commitDiff.commitId ASC, changed.filePosition ASC
            """)
    List<ChangedFileRow> findChangedFilesByTask(
            @Param("taskId") Integer taskId
    );

    @Query("""
            SELECT new com.ivan_naumenko.repository_analyzer.diff.model.CommitStatsRow(
                d.commitId, c.commitHash, c.message, c.authorName, c.authorEmail,
                c.committedAt, d.totalAddedLines, d.totalDeletedLines)
            FROM CommitDiffEntity d JOIN d.commit c WHERE c.id IN :ids
            ORDER BY c.committedAt ASC, c.id ASC
            """)
    List<CommitStatsRow> findStatsByIds(@Param("ids") List<Integer> ids);

    @Query("""
            SELECT new com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileRow(
                f.commitDiff.commitId, f.oldPath, f.newPath, f.changeType, f.addedLines, f.deletedLines)
            FROM ChangedFileEntity f WHERE f.commitDiff.commitId IN :ids
            ORDER BY f.commitDiff.commitId, f.filePosition
            """)
    List<ChangedFileRow> findChangedFilesByIds(@Param("ids") List<Integer> ids);

    @Query("SELECT f FROM ChangedFileEntity f WHERE f.commitDiff.commitId = :commitId AND f.filePosition = :position")
    java.util.Optional<ChangedFileEntity> findEvidenceFile(
            @Param("commitId") Integer commitId, @Param("position") int position);
}
