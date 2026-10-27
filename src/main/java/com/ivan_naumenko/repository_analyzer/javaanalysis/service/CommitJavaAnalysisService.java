package com.ivan_naumenko.repository_analyzer.javaanalysis.service;

import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFile;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiff;
import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.ChangedJavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaFileAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CommitJavaAnalysisService {

    private final GitService gitService;
    private final JavaAnalysisService javaAnalysisService;

    public List<ChangedJavaFileAnalysis> analyze(
            Path repositoryPath,
            String commitHash,
            CommitDiff diff
    ) {
        Objects.requireNonNull(
                repositoryPath,
                "repositoryPath не должен быть null"
        );
        if (commitHash == null || commitHash.isBlank()) {
            throw new IllegalArgumentException(
                    "commitHash не должен быть пустым"
            );
        }
        Objects.requireNonNull(diff, "diff не должен быть null");

        if (!commitHash.trim().equals(diff.commitHash())) {
            throw new IllegalArgumentException(
                    "Хеш diff не совпадает с анализируемым коммитом"
            );
        }

        List<ChangedJavaFileAnalysis> analyses = new ArrayList<>();
        var sources = gitService.readFilesAtCommit(repositoryPath, commitHash,
                diff.changedFiles().stream().filter(this::canAnalyze).map(ChangedFile::newPath).distinct().toList());

        for (int position = 0;
             position < diff.changedFiles().size();
             position++) {
            ChangedFile changedFile = diff.changedFiles().get(position);

            if (!canAnalyze(changedFile)) {
                continue;
            }

            String sourceCode = sources.get(changedFile.newPath());
            JavaFileAnalysis analysis = javaAnalysisService.analyze(
                    changedFile.newPath(),
                    sourceCode
            );
            analyses.add(new ChangedJavaFileAnalysis(position, analysis));
        }

        return List.copyOf(analyses);
    }

    private boolean canAnalyze(ChangedFile changedFile) {
        return changedFile.javaFile()
                && changedFile.changeType() != FileChangeType.DELETE
                && changedFile.newPath() != null;
    }
}
