package com.ivan_naumenko.repository_analyzer.analysis.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.repository.AnalysisTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AnalysisTaskServiceTest {

    @Mock
    private AnalysisTaskRepository repository;

    @Test
    void normalizesRepositoryUrlAndBranchBeforeSaving() {
        AnalysisTaskService service = new AnalysisTaskService(repository);
        when(repository.findByRepositoryUrlAndBranch(
                "https://example.com/project.git",
                "main"
        )).thenReturn(Optional.empty());
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(AnalysisTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AnalysisTask result = service.createOrGet(
                "  https://example.com/project.git  ",
                "  main  "
        );

        assertEquals("https://example.com/project.git", result.getRepositoryUrl());
        assertEquals("main", result.getBranch());

        ArgumentCaptor<AnalysisTask> taskCaptor = ArgumentCaptor.forClass(AnalysisTask.class);
        verify(repository).saveAndFlush(taskCaptor.capture());
        assertSame(result, taskCaptor.getValue());
    }

    @Test
    void returnsTaskCreatedByAnotherRequestAfterUniqueConstraintViolation() {
        AnalysisTaskService service = new AnalysisTaskService(repository);
        AnalysisTask existingTask = new AnalysisTask();
        String repositoryUrl = "https://example.com/project.git";
        String branch = "main";

        when(repository.findByRepositoryUrlAndBranch(repositoryUrl, branch))
                .thenReturn(Optional.empty(), Optional.of(existingTask));
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(AnalysisTask.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        AnalysisTask result = service.createOrGet(repositoryUrl, branch);

        assertSame(existingTask, result);
    }

    @Test
    void doesNotHideOtherDatabaseFailures() {
        AnalysisTaskService service = new AnalysisTaskService(repository);
        String repositoryUrl = "https://example.com/project.git";
        String branch = "main";
        DataAccessResourceFailureException databaseFailure =
                new DataAccessResourceFailureException("database unavailable");

        when(repository.findByRepositoryUrlAndBranch(repositoryUrl, branch))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(AnalysisTask.class)))
                .thenThrow(databaseFailure);

        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> service.createOrGet(repositoryUrl, branch)
        );

        assertSame(databaseFailure, thrown);
    }
}
