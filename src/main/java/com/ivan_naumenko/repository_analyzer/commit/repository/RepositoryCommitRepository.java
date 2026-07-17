package com.ivan_naumenko.repository_analyzer.commit.repository;

import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepositoryCommitRepository extends JpaRepository<RepositoryCommit, Integer> {

    List<RepositoryCommit> findAllByAnalysisTask_Id(Integer analysisTaskId);
}
