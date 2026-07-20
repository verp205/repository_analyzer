package com.ivan_naumenko.repository_analyzer.commit.service;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingData;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiff;
import com.ivan_naumenko.repository_analyzer.diff.service.CommitResultService;
import com.ivan_naumenko.repository_analyzer.diff.service.DiffService;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import com.ivan_naumenko.repository_analyzer.javaanalysis.service.CommitJavaAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultCommitProcessorTest {

    private static final Integer COMMIT_ID = 11;
    private static final String COMMIT_HASH = "a".repeat(40);
    private static final String REPOSITORY_URL =
            "https://example.com/project.git";
    private static final String BRANCH = "main";
    private static final Path REPOSITORY_PATH = Path.of("repository");

    @Mock
    private RepositoryCommitRepository commitRepository;

    @Mock
    private GitService gitService;

    @Mock
    private DiffService diffService;

    @Mock
    private CommitResultService commitResultService;

    @Mock
    private CommitJavaAnalysisService commitJavaAnalysisService;

    @Test
    void extractsAndSavesDiffForClaimedCommit() {
        DefaultCommitProcessor processor = processor();
        CommitProcessingData data = processingData();
        CommitDiff diff = new CommitDiff(
                COMMIT_HASH,
                null,
                List.of(),
                0,
                0,
                ""
        );
        when(commitRepository.findProcessingData(
                COMMIT_ID,
                CommitProcessingStatus.PROCESSING
        )).thenReturn(Optional.of(data));
        when(gitService.getRepositoryPath(REPOSITORY_URL, BRANCH))
                .thenReturn(REPOSITORY_PATH);
        when(diffService.readDiff(REPOSITORY_PATH, COMMIT_HASH))
                .thenReturn(diff);
        when(commitJavaAnalysisService.analyze(
                REPOSITORY_PATH,
                COMMIT_HASH,
                diff
        )).thenReturn(List.of());

        processor.process(COMMIT_ID);

        verify(commitResultService).complete(COMMIT_ID, diff, List.of());
        verify(commitResultService, never()).fail(eq(COMMIT_ID), any());
    }

    @Test
    void marksCommitAsFailedWhenDiffCannotBeRead() {
        DefaultCommitProcessor processor = processor();
        CommitProcessingData data = processingData();
        IllegalStateException failure = new IllegalStateException("diff failed");
        when(commitRepository.findProcessingData(
                COMMIT_ID,
                CommitProcessingStatus.PROCESSING
        )).thenReturn(Optional.of(data));
        when(gitService.getRepositoryPath(REPOSITORY_URL, BRANCH))
                .thenReturn(REPOSITORY_PATH);
        when(diffService.readDiff(REPOSITORY_PATH, COMMIT_HASH))
                .thenThrow(failure);
        when(commitResultService.fail(COMMIT_ID, failure)).thenReturn(true);

        processor.process(COMMIT_ID);

        verify(commitResultService).fail(COMMIT_ID, failure);
        verify(commitJavaAnalysisService, never()).analyze(
                any(),
                any(),
                any()
        );
        verify(commitResultService, never()).complete(
                eq(COMMIT_ID),
                any(),
                any()
        );
    }

    @Test
    void marksCommitAsFailedWhenJavaAnalysisCannotBeCompleted() {
        DefaultCommitProcessor processor = processor();
        CommitProcessingData data = processingData();
        CommitDiff diff = new CommitDiff(
                COMMIT_HASH,
                null,
                List.of(),
                0,
                0,
                ""
        );
        IllegalStateException failure = new IllegalStateException(
                "java analysis failed"
        );
        when(commitRepository.findProcessingData(
                COMMIT_ID,
                CommitProcessingStatus.PROCESSING
        )).thenReturn(Optional.of(data));
        when(gitService.getRepositoryPath(REPOSITORY_URL, BRANCH))
                .thenReturn(REPOSITORY_PATH);
        when(diffService.readDiff(REPOSITORY_PATH, COMMIT_HASH))
                .thenReturn(diff);
        when(commitJavaAnalysisService.analyze(
                REPOSITORY_PATH,
                COMMIT_HASH,
                diff
        )).thenThrow(failure);
        when(commitResultService.fail(COMMIT_ID, failure)).thenReturn(true);

        processor.process(COMMIT_ID);

        verify(commitResultService).fail(COMMIT_ID, failure);
        verify(commitResultService, never()).complete(
                eq(COMMIT_ID),
                any(),
                any()
        );
    }

    private DefaultCommitProcessor processor() {
        return new DefaultCommitProcessor(
                commitRepository,
                gitService,
                diffService,
                commitJavaAnalysisService,
                commitResultService
        );
    }

    private CommitProcessingData processingData() {
        return new CommitProcessingData(
                COMMIT_ID,
                7,
                COMMIT_HASH,
                REPOSITORY_URL,
                BRANCH
        );
    }
}
