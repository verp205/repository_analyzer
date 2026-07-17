package com.ivan_naumenko.repository_analyzer.commit.repository;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitParent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommitParentRepository extends JpaRepository<CommitParent, Integer> {

    List<CommitParent> findAllByChildCommit_AnalysisTask_Id(
            Integer analysisTaskId
    );
}
