package com.ivan_naumenko.repository_analyzer.llm.context.service;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingData;
import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
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
import com.ivan_naumenko.repository_analyzer.javaanalysis.repository.JavaTypeAnalysisRepository;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactCommitContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoredCommitContextService {

    private final CommitDiffRepository diffRepository;
    private final JavaFileAnalysisRepository javaAnalysisRepository;
    private final CompactContextService compactContextService;
    private final JavaTypeAnalysisRepository javaTypeAnalysisRepository;

    @Transactional(readOnly = true)
    public CompactCommitContext load(Integer commitId) {
        return loadAll(List.of(commitId)).get(commitId);
    }

    @Transactional(readOnly = true)
    public Map<Integer, CompactCommitContext> loadAll(List<Integer> commitIds) {
        if (commitIds.isEmpty()) return Map.of();
        Map<Integer, CompactCommitContext> result = new LinkedHashMap<>();
        List<Integer> ids = commitIds.stream().distinct().toList();
        // Bound IN clauses while avoiding one query per file/type/method.
        for (int offset = 0; offset < ids.size(); offset += 400) {
            List<Integer> part = ids.subList(offset, Math.min(offset + 400, ids.size()));
            List<CommitDiffEntity> diffs = diffRepository.findForContext(part);
            Map<Integer, JavaFileAnalysisEntity> analyses = javaAnalysisRepository.findForContext(part).stream()
                    .collect(Collectors.toMap(JavaFileAnalysisEntity::getChangedFileId, entity -> entity));
            javaTypeAnalysisRepository.findForContext(part); // Initializes methods in the same persistence context.
            for (CommitDiffEntity diff : diffs) result.put(diff.getCommitId(), toContext(diff, analyses));
        }
        for (Integer id : ids) if (!result.containsKey(id)) throw new IllegalStateException("Для коммита " + id + " не сохранён diff");
        return result;
    }

    private CompactCommitContext toContext(CommitDiffEntity diffEntity, Map<Integer, JavaFileAnalysisEntity> analyses) {
        RepositoryCommit commit = diffEntity.getCommit();
        CommitDiff diff = toDiff(commit, diffEntity);
        List<ChangedJavaFileAnalysis> javaAnalyses = toJavaAnalyses(diffEntity, analyses);
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
            CommitDiffEntity diff, Map<Integer, JavaFileAnalysisEntity> byFile
    ) {
        List<ChangedJavaFileAnalysis> analyses = new ArrayList<>();

        for (ChangedFileEntity changedFile : diff.getChangedFiles()) {
            JavaFileAnalysisEntity entity = byFile.get(changedFile.getId());
            if (entity != null) analyses.add(new ChangedJavaFileAnalysis(changedFile.getFilePosition(), toJavaFileAnalysis(entity)));
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
