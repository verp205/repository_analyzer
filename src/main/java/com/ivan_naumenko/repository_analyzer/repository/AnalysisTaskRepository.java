package com.ivan_naumenko.repository_analyzer.repository;

import com.ivan_naumenko.repository_analyzer.model.AnalysisTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisTaskRepository extends JpaRepository<AnalysisTask, Integer> {

    Optional<AnalysisTask> findByRepositoryUrlAndBranch(
            String repositoryUrl,
            String branch
    );

}
