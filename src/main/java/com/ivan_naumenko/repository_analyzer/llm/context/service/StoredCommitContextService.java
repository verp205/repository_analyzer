package com.ivan_naumenko.repository_analyzer.llm.context.service;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingData;
import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFile;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileEntity;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiff;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiffEntity;
import com.ivan_naumenko.repository_analyzer.diff.repository.CommitDiffRepository;
import com.ivan_naumenko.repository_analyzer.javaanalysis.entity.JavaFileAnalysisEntity;
import com.ivan_naumenko.repository_analyzer.javaanalysis.entity.JavaMethodAnalysisEntity;
import com.ivan_naumenko.repository_analyzer.javaanalysis.entity.JavaTypeAnalysisEntity;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.ChangedJavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaMethodAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaTypeAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.repository.JavaFileAnalysisRepository;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactCommitContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StoredCommitContextService {

    private final RepositoryCommitRepository commitRepository;
    private final CommitDiffRepository diffRepository;
    private final JavaFileAnalysisRepository javaAnalysisRepository;
    private final CompactContextService compactContextService;

    @Transactional(readOnly = true)
    public CompactCommitContext load(Integer commitId) {
        RepositoryCommit commit = commitRepository.findById(commitId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Коммит не найден: " + commitId
                ));
        CommitDiffEntity diffEntity = diffRepository.findById(commitId)
                .orElseThrow(() -> new IllegalStateException(
                        "Для коммита " + commitId + " не сохранён diff"
                ));

        CommitDiff diff = toDiff(commit, diffEntity);
        List<ChangedJavaFileAnalysis> javaAnalyses = toJavaAnalyses(diffEntity);
        CommitProcessingData processingData = new CommitProcessingData(
                commit.getId(),
                commit.getAnalysisTask().getId(),
                commit.getCommitHash(),
                commit.getAnalysisTask().getRepositoryUrl(),
                commit.getAnalysisTask().getBranch(),
                commit.getAuthorName(),
                commit.getCommittedAt(),
                commit.getMessage()
        );

        return compactContextService.build(
                processingData,
                diff,
                javaAnalyses
        );
    }

    private CommitDiff toDiff(
            RepositoryCommit commit,
            CommitDiffEntity entity
    ) {
        List<ChangedFile> changedFiles = entity.getChangedFiles()
                .stream()
                .map(this::toChangedFile)
                .toList();

        return new CommitDiff(
                commit.getCommitHash(),
                entity.getParentHash(),
                changedFiles,
                entity.getTotalAddedLines(),
                entity.getTotalDeletedLines(),
                entity.getRawDiff()
        );
    }

    private ChangedFile toChangedFile(ChangedFileEntity entity) {
        return new ChangedFile(
                entity.getOldPath(),
                entity.getNewPath(),
                entity.getChangeType(),
                entity.getAddedLines(),
                entity.getDeletedLines(),
                entity.isJavaFile(),
                entity.getPatch()
        );
    }

    private List<ChangedJavaFileAnalysis> toJavaAnalyses(
            CommitDiffEntity diff
    ) {
        List<ChangedJavaFileAnalysis> analyses = new ArrayList<>();

        for (ChangedFileEntity changedFile : diff.getChangedFiles()) {
            javaAnalysisRepository.findById(changedFile.getId())
                    .ifPresent(entity -> analyses.add(
                            new ChangedJavaFileAnalysis(
                                    changedFile.getFilePosition(),
                                    toJavaFileAnalysis(entity)
                            )
                    ));
        }

        return List.copyOf(analyses);
    }

    private JavaFileAnalysis toJavaFileAnalysis(
            JavaFileAnalysisEntity entity
    ) {
        return new JavaFileAnalysis(
                entity.getChangedFile().getNewPath(),
                entity.getPackageName(),
                split(entity.getImportsText()),
                entity.getTypes().stream().map(this::toTypeAnalysis).toList(),
                entity.getLineCount(),
                entity.isParsingSuccessful(),
                split(entity.getProblemsText())
        );
    }

    private JavaTypeAnalysis toTypeAnalysis(JavaTypeAnalysisEntity entity) {
        return new JavaTypeAnalysis(
                entity.getName(),
                entity.getKind(),
                split(entity.getModifiersText()),
                split(entity.getAnnotationsText()),
                entity.getMethods()
                        .stream()
                        .map(this::toMethodAnalysis)
                        .toList(),
                entity.getConstructorCount()
        );
    }

    private JavaMethodAnalysis toMethodAnalysis(
            JavaMethodAnalysisEntity entity
    ) {
        return new JavaMethodAnalysis(
                entity.getName(),
                entity.getReturnType(),
                split(entity.getParameterTypesText()),
                split(entity.getModifiersText()),
                split(entity.getAnnotationsText())
        );
    }

    private List<String> split(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return text.lines().toList();
    }
}
