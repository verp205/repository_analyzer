package com.ivan_naumenko.repository_analyzer.commitinsight.repository;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommitInsightRepository
        extends JpaRepository<CommitInsightEntity, Integer> {

    @Query("""
            SELECT insight
            FROM CommitInsightEntity insight
            WHERE insight.promptVersion = :promptVersion
              AND insight.commitId IN (
                  SELECT commit.id
                  FROM RepositoryCommit commit
                  WHERE commit.analysisTask.id = :taskId
              )
            """)
    List<CommitInsightEntity> findAllByTask(
            @Param("taskId") Integer taskId,
            @Param("promptVersion") String promptVersion
    );
}
