package com.ivan_naumenko.repository_analyzer.javaanalysis.service;

import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFile;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiff;
import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.ChangedJavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaFileAnalysis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommitJavaAnalysisServiceTest {

    private static final Path REPOSITORY_PATH = Path.of("repository");
    private static final String COMMIT_HASH = "a".repeat(40);

    @Mock
    private GitService gitService;

    @Mock
    private JavaAnalysisService javaAnalysisService;

    @Test
    void analyzesOnlyJavaFilesPresentInCurrentCommit() {
        ChangedFile javaFile = changedFile(
                null,
                "src/Sample.java",
                FileChangeType.ADD,
                true,
                2,
                0
        );
        ChangedFile deletedJavaFile = changedFile(
                "src/Old.java",
                null,
                FileChangeType.DELETE,
                true,
                0,
                3
        );
        ChangedFile textFile = changedFile(
                "README.md",
                "README.md",
                FileChangeType.MODIFY,
                false,
                1,
                1
        );
        CommitDiff diff = new CommitDiff(
                COMMIT_HASH,
                null,
                List.of(javaFile, deletedJavaFile, textFile),
                3,
                4,
                "diff"
        );
        String sourceCode = "class Sample {}";
        JavaFileAnalysis analysis = new JavaFileAnalysis(
                javaFile.newPath(),
                "",
                List.of(),
                List.of(),
                1,
                true,
                List.of()
        );
        when(gitService.readFileAtCommit(
                REPOSITORY_PATH,
                COMMIT_HASH,
                javaFile.newPath()
        )).thenReturn(sourceCode);
        when(javaAnalysisService.analyze(javaFile.newPath(), sourceCode))
                .thenReturn(analysis);

        CommitJavaAnalysisService service = service();
        List<ChangedJavaFileAnalysis> result = service.analyze(
                REPOSITORY_PATH,
                COMMIT_HASH,
                diff
        );

        assertEquals(
                List.of(new ChangedJavaFileAnalysis(0, analysis)),
                result
        );
        verify(gitService, never()).readFileAtCommit(
                REPOSITORY_PATH,
                COMMIT_HASH,
                "src/Old.java"
        );
        verify(gitService, never()).readFileAtCommit(
                REPOSITORY_PATH,
                COMMIT_HASH,
                "README.md"
        );
    }

    @Test
    void rejectsDiffFromAnotherCommitBeforeReadingRepository() {
        CommitDiff diff = new CommitDiff(
                "b".repeat(40),
                null,
                List.of(),
                0,
                0,
                ""
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service().analyze(
                        REPOSITORY_PATH,
                        COMMIT_HASH,
                        diff
                )
        );

        verify(gitService, never()).readFileAtCommit(
                REPOSITORY_PATH,
                COMMIT_HASH,
                "unused.java"
        );
    }

    private ChangedFile changedFile(
            String oldPath,
            String newPath,
            FileChangeType changeType,
            boolean javaFile,
            int addedLines,
            int deletedLines
    ) {
        return new ChangedFile(
                oldPath,
                newPath,
                changeType,
                addedLines,
                deletedLines,
                javaFile,
                "patch"
        );
    }

    private CommitJavaAnalysisService service() {
        return new CommitJavaAnalysisService(gitService, javaAnalysisService);
    }
}
