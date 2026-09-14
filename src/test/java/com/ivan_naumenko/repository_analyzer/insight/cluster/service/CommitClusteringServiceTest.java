package com.ivan_naumenko.repository_analyzer.insight.cluster.service;

import com.ivan_naumenko.repository_analyzer.insight.cluster.model.ChangeClusterType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommitClusteringServiceTest {

    @Test
    void avoidsSubstringFalsePositives() {
        assertEquals(
                ChangeClusterType.CORE,
                CommitClusteringService.classify(
                        "Restore author info",
                        List.of("src/main/java/app/AuthorService.java")
                )
        );
        assertEquals(
                ChangeClusterType.CORE,
                CommitClusteringService.classifyPath(
                        "src/main/java/com/example/repository_analyzer/diff/service/DiffService.java"
                )
        );
    }

    @Test
    void understandsRussianMessages() {
        assertEquals(
                ChangeClusterType.DOCUMENTATION,
                CommitClusteringService.classify("Исправление readme файла", List.of("README.md"))
        );
        assertEquals(
                ChangeClusterType.SECURITY,
                CommitClusteringService.classify(
                        "Доработка авторизации",
                        List.of("src/main/java/app/UserService.java")
                )
        );
    }

    @Test
    void weightsSecurityFilesAboveOrdinaryCode() {
        assertEquals(
                ChangeClusterType.SECURITY,
                CommitClusteringService.classify(
                        "feature",
                        List.of(
                                "src/main/java/app/security/JwtFilter.java",
                                "src/main/java/app/Core.java",
                                "src/main/java/app/Other.java"
                        )
                )
        );
    }

    @Test
    void classifiesTypicalPaths() {
        assertEquals(ChangeClusterType.TESTS,
                CommitClusteringService.classifyPath("src/test/java/app/ParserTest.java"));
        assertEquals(ChangeClusterType.TESTS,
                CommitClusteringService.classifyPath("frontend/src/lib/api.spec.ts"));
        assertEquals(ChangeClusterType.BUILD_AND_CONFIGURATION,
                CommitClusteringService.classifyPath("pom.xml"));
        assertEquals(ChangeClusterType.DATA,
                CommitClusteringService.classifyPath("src/main/resources/db/migration/V1__init.sql"));
        assertEquals(ChangeClusterType.DATA,
                CommitClusteringService.classifyPath("src/main/java/app/CommitDiffRepository.java"));
        assertEquals(ChangeClusterType.API,
                CommitClusteringService.classifyPath("src/main/java/app/ReportController.java"));
    }
}
