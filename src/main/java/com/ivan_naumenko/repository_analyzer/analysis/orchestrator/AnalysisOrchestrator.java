package com.ivan_naumenko.repository_analyzer.analysis.orchestrator;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.commit.service.CommitDiscoveryService;
import com.ivan_naumenko.repository_analyzer.git.model.GitCommit;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@Component
public class AnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(
            AnalysisOrchestrator.class
    );

    private final AnalysisTaskService analysisTaskService;
    private final GitService gitService;
    private final CommitDiscoveryService commitDiscoveryService;
    private final ExecutorService analysisExecutor;

    public AnalysisOrchestrator(
            AnalysisTaskService analysisTaskService,
            GitService gitService,
            CommitDiscoveryService commitDiscoveryService,
            @Qualifier("analysisExecutor")
            ExecutorService analysisExecutor
    ) {
        this.analysisTaskService = analysisTaskService;
        this.gitService = gitService;
        this.commitDiscoveryService = commitDiscoveryService;
        this.analysisExecutor = analysisExecutor;
    }

    public boolean start(Integer taskId) {
        Objects.requireNonNull(taskId, "taskId не должен быть null");

        boolean taskClaimed = analysisTaskService.updateStatusIfCurrent(
                taskId,
                AnalysisTaskStatus.NEW,
                AnalysisTaskStatus.CLONING
        );

        if (!taskClaimed) {
            log.debug(
                    "Задача анализа {} уже запущена или не существует",
                    taskId
            );
            return false;
        }

        try {
            analysisExecutor.execute(() -> runAnalysis(taskId));
            return true;
        } catch (RejectedExecutionException exception) {
            returnTaskToNew(taskId, exception);
            throw exception;
        }
    }

    private void runAnalysis(Integer taskId) {
        try {
            AnalysisTask task = analysisTaskService.getById(taskId);

            gitService.cloneOrUpdateRepository(
                    task.getRepositoryUrl(),
                    task.getBranch()
            );

            boolean discoveryStarted = analysisTaskService.updateStatusIfCurrent(
                    taskId,
                    AnalysisTaskStatus.CLONING,
                    AnalysisTaskStatus.DISCOVERING_COMMITS
            );

            if (!discoveryStarted) {
                log.warn(
                        "Задача {} больше не находится в статусе CLONING; "
                                + "чтение истории отменено",
                        taskId
                );
                return;
            }

            List<GitCommit> commits = gitService.readHistory(
                    task.getRepositoryUrl(),
                    task.getBranch()
            );

            int savedCommits = commitDiscoveryService.saveDiscoveredCommits(
                    taskId,
                    commits
            );

            log.info(
                    "Для задачи {} обнаружено {} коммитов, новых сохранено {}",
                    taskId,
                    commits.size(),
                    savedCommits
            );

            boolean processingStarted = analysisTaskService.updateStatusIfCurrent(
                    taskId,
                    AnalysisTaskStatus.DISCOVERING_COMMITS,
                    AnalysisTaskStatus.PROCESSING
            );

            if (!processingStarted) {
                log.warn(
                        "Задача {} больше не находится в статусе "
                                + "DISCOVERING_COMMITS",
                        taskId
                );
            }
        } catch (Exception exception) {
            markTaskAsFailed(taskId, exception);
        }
    }

    private void returnTaskToNew(
            Integer taskId,
            RejectedExecutionException rejection
    ) {
        try {
            boolean returned = analysisTaskService.updateStatusIfCurrent(
                    taskId,
                    AnalysisTaskStatus.CLONING,
                    AnalysisTaskStatus.NEW
            );

            if (!returned) {
                log.error(
                        "Executor отклонил задачу {}, но вернуть её "
                                + "из CLONING в NEW не удалось",
                        taskId
                );
            }
        } catch (Exception statusException) {
            rejection.addSuppressed(statusException);
        }
    }

    private void markTaskAsFailed(Integer taskId, Exception cause) {
        try {
            boolean markedAsFailed = analysisTaskService.updateStatusIfCurrent(
                    taskId,
                    AnalysisTaskStatus.CLONING,
                    AnalysisTaskStatus.FAILED
            );

            if (!markedAsFailed) {
                markedAsFailed = analysisTaskService.updateStatusIfCurrent(
                        taskId,
                        AnalysisTaskStatus.DISCOVERING_COMMITS,
                        AnalysisTaskStatus.FAILED
                );
            }

            if (!markedAsFailed) {
                log.error(
                        "Анализ задачи {} завершился ошибкой, но статус FAILED "
                                + "установить не удалось",
                        taskId,
                        cause
                );
                return;
            }

            log.error("Анализ задачи {} завершился ошибкой", taskId, cause);
        } catch (Exception statusException) {
            cause.addSuppressed(statusException);
            log.error(
                    "Анализ задачи {} завершился ошибкой; дополнительная "
                            + "ошибка возникла при установке статуса FAILED",
                    taskId,
                    cause
            );
        }
    }
}
