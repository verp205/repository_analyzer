package com.ivan_naumenko.repository_analyzer.javaanalysis.repository;

import com.ivan_naumenko.repository_analyzer.javaanalysis.entity.JavaFileAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface JavaFileAnalysisRepository
        extends JpaRepository<JavaFileAnalysisEntity, Integer> {
    @Query("SELECT DISTINCT a FROM JavaFileAnalysisEntity a LEFT JOIN FETCH a.types WHERE a.changedFile.commitDiff.commitId IN :ids")
    List<JavaFileAnalysisEntity> findForContext(@Param("ids") List<Integer> ids);
}
