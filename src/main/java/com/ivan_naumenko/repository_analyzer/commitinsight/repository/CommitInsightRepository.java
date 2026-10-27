package com.ivan_naumenko.repository_analyzer.commitinsight.repository;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommitInsightRepository
        extends JpaRepository<CommitInsightEntity, Integer> {

    @Query("SELECT i.commitId FROM CommitInsightEntity i WHERE i.promptVersion = :version AND i.status <> com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightStatus.FAILED AND i.commitId IN (SELECT c.id FROM RepositoryCommit c WHERE c.analysisTask.id = :taskId)")
    List<Integer> findReusableIds(@Param("taskId") Integer taskId, @Param("version") String version);

    @Query("SELECT i FROM CommitInsightEntity i WHERE i.promptVersion = :version AND i.commitId IN :ids AND i.commitId IN (SELECT c.id FROM RepositoryCommit c WHERE c.analysisTask.id = :taskId)")
    List<CommitInsightEntity> findForCommits(@Param("taskId") Integer taskId, @Param("ids") List<Integer> ids, @Param("version") String version);

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
