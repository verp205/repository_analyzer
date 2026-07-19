package com.ivan_naumenko.repository_analyzer.diff.repository;

import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiffEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommitDiffRepository
        extends JpaRepository<CommitDiffEntity, Integer> {
}
