package com.ivan_naumenko.repository_analyzer.javaanalysis.repository;

import com.ivan_naumenko.repository_analyzer.javaanalysis.entity.JavaTypeAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface JavaTypeAnalysisRepository extends JpaRepository<JavaTypeAnalysisEntity, Integer> {
    @Query("SELECT DISTINCT t FROM JavaTypeAnalysisEntity t LEFT JOIN FETCH t.methods WHERE t.javaFileAnalysis.changedFile.commitDiff.commitId IN :ids")
    List<JavaTypeAnalysisEntity> findForContext(@Param("ids") List<Integer> ids);
}
