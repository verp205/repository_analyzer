package com.ivan_naumenko.repository_analyzer.commit.repository;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingData;
import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RepositoryCommitRepository extends JpaRepository<RepositoryCommit, Integer> {

    List<RepositoryCommit> findAllByAnalysisTask_Id(Integer analysisTaskId);

    long countByAnalysisTask_IdAndProcessingStatusIn(
            Integer analysisTaskId,
            Collection<CommitProcessingStatus> statuses
    );

    boolean existsByAnalysisTask_IdAndProcessingStatus(
            Integer analysisTaskId,
            CommitProcessingStatus status
    );

    @Query("""
            SELECT new com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingData(
                commit.id,
                commit.analysisTask.id,
                commit.commitHash,
                commit.analysisTask.repositoryUrl,
                commit.analysisTask.branch,
                commit.authorName,
                commit.committedAt,
                commit.message
            )
            FROM RepositoryCommit commit
            WHERE commit.id = :commitId
              AND commit.processingStatus = :status
            """)
    Optional<CommitProcessingData> findProcessingData(
            @Param("commitId") Integer commitId,
            @Param("status") CommitProcessingStatus status
    );

    @Query(value = """
                SELECT id
                FROM repository_commit
                WHERE analysis_task_id = :taskId
                  AND processing_status = 'NEW'
                ORDER BY RANDOM()
                LIMIT 1
                """,
            nativeQuery = true
    )
    Optional<Integer> findRandomNewCommitId(
            @Param("taskId") Integer taskId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE RepositoryCommit r
            SET r.processingStatus = :newStatus,
                r.attemptCount = r.attemptCount + 1,
                r.updatedAt = :updatedAt
            WHERE r.id = :commitId
              AND r.processingStatus = :expectedStatus
            """)
    int updateProcessingStatusIfCurrent(
            @Param("commitId") Integer commitId,
            @Param("expectedStatus") CommitProcessingStatus expectedStatus,
            @Param("newStatus") CommitProcessingStatus newStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE RepositoryCommit r
            SET r.processingStatus = :newStatus,
                r.lastError = :lastError,
                r.updatedAt = :updatedAt
            WHERE r.id = :commitId
              AND r.processingStatus = :expectedStatus
            """)
    int finishProcessingIfCurrent(
            @Param("commitId") Integer commitId,
            @Param("expectedStatus") CommitProcessingStatus expectedStatus,
            @Param("newStatus") CommitProcessingStatus newStatus,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        UPDATE RepositoryCommit r
        SET r.processingStatus = :newStatus,
            r.updatedAt = :updatedAt
        WHERE r.analysisTask.id = :taskId
          AND r.processingStatus = :expectedStatus
        """)
    int requeueInterruptedCommits(
            @Param("taskId") Integer taskId,
            @Param("expectedStatus") CommitProcessingStatus expectedStatus,
            @Param("newStatus") CommitProcessingStatus newStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        UPDATE RepositoryCommit r
        SET r.processingStatus = :newStatus,
            r.updatedAt = :updatedAt
        WHERE r.analysisTask.id = :taskId
          AND r.processingStatus = :expectedStatus
          AND r.attemptCount < :maxAttempts
          AND r.analysisTask.status = :taskStatus
        """)
    int requeueRetryableFailedCommits(
            @Param("taskId") Integer taskId,
            @Param("expectedStatus") CommitProcessingStatus expectedStatus,
            @Param("newStatus") CommitProcessingStatus newStatus,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("maxAttempts") int maxAttempts,
            @Param("taskStatus") AnalysisTaskStatus taskStatus
    );

}
