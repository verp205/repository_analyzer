package com.ivan_naumenko.repository_analyzer.commit.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitParent;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import com.ivan_naumenko.repository_analyzer.commit.repository.CommitParentRepository;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.git.model.GitCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommitDiscoveryService {

    private final AnalysisTaskService analysisTaskService;
    private final RepositoryCommitRepository commitRepository;
    private final CommitParentRepository parentRepository;

    @Transactional
    public int saveDiscoveredCommits(
            Integer taskId,
            List<GitCommit> discoveredCommits
    ) {
        Objects.requireNonNull(taskId, "taskId не должен быть null");
        Objects.requireNonNull(
                discoveredCommits,
                "discoveredCommits не должен быть null"
        );

        AnalysisTask task = analysisTaskService.getById(taskId);
        Map<String, RepositoryCommit> commitsByHash = loadCommitsByHash(taskId);
        Set<String> knownHashes = new HashSet<>(commitsByHash.keySet());
        LocalDateTime now = LocalDateTime.now();
        List<RepositoryCommit> newCommits = new ArrayList<>();

        for (GitCommit gitCommit : discoveredCommits) {
            if (knownHashes.add(gitCommit.commitHash())) {
                newCommits.add(toEntity(task, gitCommit, now));
            }
        }

        if (!newCommits.isEmpty()) {
            commitRepository.saveAllAndFlush(newCommits);
            for (RepositoryCommit commit : newCommits) {
                commitsByHash.put(commit.getCommitHash(), commit);
            }
        }

        saveMissingParents(taskId, discoveredCommits, commitsByHash);
        return newCommits.size();
    }

    private Map<String, RepositoryCommit> loadCommitsByHash(Integer taskId) {
        Map<String, RepositoryCommit> commitsByHash = new HashMap<>();

        for (RepositoryCommit commit
                : commitRepository.findAllByAnalysisTask_Id(taskId)) {
            commitsByHash.put(commit.getCommitHash(), commit);
        }

        return commitsByHash;
    }

    private RepositoryCommit toEntity(
            AnalysisTask task,
            GitCommit gitCommit,
            LocalDateTime now
    ) {
        RepositoryCommit commit = new RepositoryCommit();
        commit.setAnalysisTask(task);
        commit.setCommitHash(gitCommit.commitHash());
        commit.setAuthorName(gitCommit.authorName());
        commit.setAuthorEmail(gitCommit.authorMail());
        commit.setCommittedAt(gitCommit.committedAt());
        commit.setMessage(gitCommit.message());
        commit.setProcessingStatus(CommitProcessingStatus.NEW);
        commit.setAttemptCount(0);
        commit.setCreatedAt(now);
        commit.setUpdatedAt(now);
        return commit;
    }

    private void saveMissingParents(
            Integer taskId,
            List<GitCommit> discoveredCommits,
            Map<String, RepositoryCommit> commitsByHash
    ) {
        Map<ParentKey, String> existingParents = loadExistingParents(taskId);
        List<CommitParent> newParents = new ArrayList<>();

        for (GitCommit gitCommit : discoveredCommits) {
            RepositoryCommit childCommit = commitsByHash.get(
                    gitCommit.commitHash()
            );

            if (childCommit == null) {
                throw new IllegalStateException(
                        "Не найден сохранённый коммит " + gitCommit.commitHash()
                );
            }

            List<String> parentHashes = gitCommit.parentHashes();
            for (int position = 0; position < parentHashes.size(); position++) {
                ParentKey key = new ParentKey(childCommit.getId(), position);
                String parentHash = parentHashes.get(position);
                String existingHash = existingParents.putIfAbsent(key, parentHash);

                if (existingHash == null) {
                    CommitParent parent = new CommitParent();
                    parent.setChildCommit(childCommit);
                    parent.setParentHash(parentHash);
                    parent.setParentPosition(position);
                    newParents.add(parent);
                } else if (!existingHash.equals(parentHash)) {
                    throw new IllegalStateException(
                            "У коммита "
                                    + gitCommit.commitHash()
                                    + " изменился parent с позицией "
                                    + position
                    );
                }
            }
        }

        if (!newParents.isEmpty()) {
            parentRepository.saveAll(newParents);
        }
    }

    private Map<ParentKey, String> loadExistingParents(Integer taskId) {
        Map<ParentKey, String> parents = new HashMap<>();

        for (CommitParent parent
                : parentRepository.findAllByChildCommit_AnalysisTask_Id(taskId)) {
            ParentKey key = new ParentKey(
                    parent.getChildCommit().getId(),
                    parent.getParentPosition()
            );
            parents.put(key, parent.getParentHash());
        }

        return parents;
    }

    private record ParentKey(Integer childCommitId, Integer position) {
    }
}
