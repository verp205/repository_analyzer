package com.ivan_naumenko.repository_analyzer.analysis.orchestrator;

import com.ivan_naumenko.repository_analyzer.analysis.config.AnalysisProperties;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.commit.service.CommitDiscoveryService;
import com.ivan_naumenko.repository_analyzer.commit.worker.CommitWorkerFactory;
import com.ivan_naumenko.repository_analyzer.git.model.GitCommit;
import com.ivan_naumenko.repository_analyzer.git.model.GitCredentials;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(
            AnalysisOrchestrator.class
    );

    private final AnalysisTaskService analysisTaskService;
    private final GitService gitService;
    private final CommitDiscoveryService commitDiscoveryService;
    private final CommitWorkerFactory commitWorkerFactory;
    private final AnalysisProperties analysisProperties;
    private final SqliteWriteCoordinator writeCoordinator;
    private final ExecutorService analysisExecutor;
    private final ConcurrentHashMap<Integer, AtomicInteger>
            activePreparations = new ConcurrentHashMap<>();

    public AnalysisOrchestrator(
            AnalysisTaskService analysisTaskService,
            GitService gitService,
            CommitDiscoveryService commitDiscoveryService,
            CommitWorkerFactory commitWorkerFactory,
            AnalysisProperties analysisProperties,
            SqliteWriteCoordinator writeCoordinator,
            @Qualifier("analysisExecutor")
            ExecutorService analysisExecutor
    ) {
        this.analysisTaskService = analysisTaskService;
        this.gitService = gitService;
        this.commitDiscoveryService = commitDiscoveryService;
        this.commitWorkerFactory = commitWorkerFactory;
        this.analysisProperties = analysisProperties;
        this.writeCoordinator = writeCoordinator;
        this.analysisExecutor = analysisExecutor;
    }

    public boolean start(Integer taskId) {
        return start(taskId, null);
    }

    public boolean start(
            Integer taskId,
            GitCredentials credentials
    ) {
        Objects.requireNonNull(taskId, "taskId не должен быть null");
        boolean submitted = false;
        markPreparationStarted(taskId);

        try {
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

            analysisExecutor.execute(
                    () -> runAnalysis(taskId, credentials)
            );
            submitted = true;
            return true;
        } catch (RejectedExecutionException exception) {
            returnTaskToNew(taskId, exception);
            throw exception;
        } finally {
            if (!submitted) {
                markPreparationFinished(taskId);
                closeCredentials(credentials);
            }
        }
    }

    private void runAnalysis(
            Integer taskId,
            GitCredentials credentials
    ) {
        try {
            AnalysisTask task = analysisTaskService.getById(taskId);

            if (credentials == null) {
                gitService.cloneOrUpdateRepository(
                        task.getRepositoryUrl(),
                        task.getBranch()
                );
            } else {
                gitService.cloneOrUpdateRepository(
                        task.getRepositoryUrl(),
                        task.getBranch(),
                        credentials
                );
            }

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

            int savedCommits = writeCoordinator.execute(
                    () -> commitDiscoveryService.saveDiscoveredCommits(
                            taskId,
                            commits
                    )
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
                return;
            }

            startCommitWorkers(taskId);
        } catch (Exception exception) {
            markTaskAsFailed(taskId, exception);
        } finally {
            markPreparationFinished(taskId);
            closeCredentials(credentials);
        }
    }

    public boolean isPreparationRunning(Integer taskId) {
        AtomicInteger count = activePreparations.get(taskId);
        return count != null && count.get() > 0;
    }

    private void markPreparationStarted(Integer taskId) {
        activePreparations.compute(
                taskId,
                (ignored, count) -> {
                    if (count == null) {
                        return new AtomicInteger(1);
                    }
                    count.incrementAndGet();
                    return count;
                }
        );
    }

    private void markPreparationFinished(Integer taskId) {
        activePreparations.computeIfPresent(
                taskId,
                (ignored, count) -> count.decrementAndGet() == 0
                        ? null
                        : count
        );
    }

    private void closeCredentials(GitCredentials credentials) {
        if (credentials != null) {
            credentials.close();
        }
    }

    private void startCommitWorkers(Integer taskId) {
        for (int workerNumber = 0;
             workerNumber < analysisProperties.maxThreads();
             workerNumber++) {
            analysisExecutor.execute(commitWorkerFactory.create(taskId));
        }

        log.info(
                "Для задачи {} запущено {} commit worker-ов",
                taskId,
                analysisProperties.maxThreads()
        );
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
                markedAsFailed = analysisTaskService.updateStatusIfCurrent(
                        taskId,
                        AnalysisTaskStatus.PROCESSING,
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
