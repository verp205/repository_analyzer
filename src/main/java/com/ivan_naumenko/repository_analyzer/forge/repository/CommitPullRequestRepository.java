package com.ivan_naumenko.repository_analyzer.forge.repository;

import com.ivan_naumenko.repository_analyzer.forge.model.CommitPullRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommitPullRequestRepository
        extends JpaRepository<CommitPullRequestEntity, Integer> {
}
