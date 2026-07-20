package com.ivan_naumenko.repository_analyzer.javaanalysis.repository;

import com.ivan_naumenko.repository_analyzer.javaanalysis.entity.JavaFileAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JavaFileAnalysisRepository
        extends JpaRepository<JavaFileAnalysisEntity, Integer> {
}
