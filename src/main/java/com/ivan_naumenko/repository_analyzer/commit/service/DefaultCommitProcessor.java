package com.ivan_naumenko.repository_analyzer.commit.service;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingData;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiff;
import com.ivan_naumenko.repository_analyzer.diff.service.CommitResultService;
import com.ivan_naumenko.repository_analyzer.diff.service.DiffService;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.ChangedJavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.service.CommitJavaAnalysisService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DefaultCommitProcessor implements CommitProcessor {

    private static final Logger log = LoggerFactory.getLogger(
            DefaultCommitProcessor.class
    );

    private final RepositoryCommitRepository commitRepository;
    private final GitService gitService;
    private final DiffService diffService;
    private final CommitJavaAnalysisService commitJavaAnalysisService;
    private final CommitResultService commitResultService;

    @Override
    public void process(Integer commitId) {
        Objects.requireNonNull(commitId, "commitId не должен быть null");

        try {
            CommitProcessingData commit = commitRepository.findProcessingData(
                            commitId,
                            CommitProcessingStatus.PROCESSING
                    )
                    .orElseThrow(() -> new IllegalStateException(
                            "Захваченный коммит "
                                    + commitId
                                    + " не найден в статусе PROCESSING"
                    ));

            Path repositoryPath = gitService.getRepositoryPath(
                    commit.repositoryUrl(),
                    commit.branch()
            );
            CommitDiff diff = diffService.readDiff(
                    repositoryPath,
                    commit.commitHash()
            );
            List<ChangedJavaFileAnalysis> javaAnalyses =
                    commitJavaAnalysisService.analyze(
                            repositoryPath,
                            commit.commitHash(),
                            diff
                    );
            commitResultService.complete(commitId, diff, javaAnalyses);

            log.info(
                    "Коммит {} обработан: файлов {}, +{}, -{}, Java-анализов {}",
                    commit.commitHash(),
                    diff.changedFiles().size(),
                    diff.totalAddedLines(),
                    diff.totalDeletedLines(),
                    javaAnalyses.size()
            );
        } catch (Exception exception) {
            handleFailure(commitId, exception);
        }
    }

    private void handleFailure(Integer commitId, Exception cause) {
        try {
            boolean markedAsFailed = commitResultService.fail(commitId, cause);
            if (!markedAsFailed) {
                log.error(
                        "Коммит {} завершился ошибкой, но уже не находится "
                                + "в статусе PROCESSING",
                        commitId,
                        cause
                );
                return;
            }
            log.error("Обработка коммита {} завершилась ошибкой", commitId, cause);
        } catch (Exception statusException) {
            cause.addSuppressed(statusException);
            log.error(
                    "Обработка коммита {} завершилась ошибкой, а статус "
                            + "FAILED сохранить не удалось",
                    commitId,
                    cause
            );
        }
    }
}
