package com.ivan_naumenko.repository_analyzer.commit.repository;

import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RepositoryCommitRepository extends JpaRepository<RepositoryCommit, Integer> {

    List<RepositoryCommit> findAllByAnalysisTask_Id(Integer analysisTaskId);

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
}
