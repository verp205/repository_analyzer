package com.ivan_naumenko.repository_analyzer.diff.repository;

import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiffEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommitDiffRepository extends JpaRepository<CommitDiffEntity, Integer> {

    List<CommitDiffEntity> findAllByCommit_AnalysisTask_Id(
            Integer analysisTaskId
    );
}
