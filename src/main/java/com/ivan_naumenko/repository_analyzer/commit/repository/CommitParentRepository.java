package com.ivan_naumenko.repository_analyzer.commit.repository;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitParent;
import com.ivan_naumenko.repository_analyzer.commit.model.ParentCountRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommitParentRepository extends JpaRepository<CommitParent, Integer> {

    @Query("SELECT p FROM CommitParent p WHERE p.childCommit.analysisTask.id = :analysisTaskId AND p.childCommit.reachable = true")
    List<CommitParent> findAllByChildCommit_AnalysisTask_Id(
            Integer analysisTaskId
    );

    @Query("""
            SELECT new com.ivan_naumenko.repository_analyzer.commit.model.ParentCountRow(
                parent.childCommit.id,
                COUNT(parent)
            )
            FROM CommitParent parent
            WHERE parent.childCommit.analysisTask.id = :taskId
            GROUP BY parent.childCommit.id
            """)
    List<ParentCountRow> countParentsByTask(@Param("taskId") Integer taskId);
}
