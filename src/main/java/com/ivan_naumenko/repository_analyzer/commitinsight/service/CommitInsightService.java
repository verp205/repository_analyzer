package com.ivan_naumenko.repository_analyzer.commitinsight.service;

import com.ivan_naumenko.repository_analyzer.commitinsight.dto.CommitInsightResponse;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDetails;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDraft;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightEntity;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightStatus;
import com.ivan_naumenko.repository_analyzer.commitinsight.repository.CommitInsightRepository;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.diff.service.TaskChangeService;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommitInsightService {

    private final CommitInsightRepository repository;
    private final TaskChangeService changeService;
    private final SqliteWriteCoordinator writeCoordinator;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public Set<Integer> findReusableCommitIds(Integer taskId) {
        return currentEntities(taskId).stream()
                .filter(entity -> entity.getStatus() != CommitInsightStatus.FAILED)
                .map(CommitInsightEntity::getCommitId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Map<Integer, CommitInsightDraft> findDrafts(Integer taskId) {
        Map<Integer, CommitInsightDraft> result = new HashMap<>();
        for (CommitInsightEntity entity : currentEntities(taskId)) {
            result.put(entity.getCommitId(), toDraft(entity));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<CommitInsightResponse> findForTask(Integer taskId) {
        return findForCommits(taskId, changeService.load(taskId));
    }

    /** Результаты разбора только для переданных коммитов (например, периода отчёта). */
    @Transactional(readOnly = true)
    public List<CommitInsightResponse> findForCommits(
            Integer taskId,
            List<CommitChangeSummary> commits
    ) {
        Map<Integer, CommitInsightDraft> drafts = findDrafts(taskId);
        return commits.stream()
                .filter(commit -> drafts.containsKey(commit.commitId()))
                .map(commit -> toResponse(commit, drafts.get(commit.commitId())))
                .toList();
    }

    public void save(Integer commitId, CommitInsightDraft draft) {
        Objects.requireNonNull(commitId, "commitId не должен быть null");
        Objects.requireNonNull(draft, "draft не должен быть null");

        CommitInsightEntity entity = new CommitInsightEntity();
        entity.setCommitId(commitId);
        entity.setPromptVersion(CommitInsightPromptFactory.PROMPT_VERSION);
        entity.setStatus(draft.status());
        entity.setCategory(draft.category());
        entity.setRiskLevel(draft.riskLevel());
        entity.setSummary(draft.summary());
        entity.setDetailsJson(mapper.writeValueAsString(draft.details()));
        entity.setCreatedAt(LocalDateTime.now());

        writeCoordinator.execute(() -> repository.save(entity));
    }

    public CommitInsightResponse toResponse(
            CommitChangeSummary commit,
            CommitInsightDraft draft
    ) {
        return new CommitInsightResponse(
                commit.commitId(),
                commit.position(),
                commit.commitHash(),
                commit.message(),
                commit.authorName(),
                commit.committedAt(),
                commit.addedLines(),
                commit.deletedLines(),
                commit.files().size(),
                draft.status(),
                draft.category(),
                draft.category().displayName(),
                draft.riskLevel(),
                draft.summary(),
                draft.details()
        );
    }

    private List<CommitInsightEntity> currentEntities(Integer taskId) {
        return repository.findAllByTask(
                taskId,
                CommitInsightPromptFactory.PROMPT_VERSION
        );
    }

    private CommitInsightDraft toDraft(CommitInsightEntity entity) {
        CommitInsightDetails details;
        try {
            details = mapper.readValue(
                    entity.getDetailsJson(),
                    CommitInsightDetails.class
            );
        } catch (RuntimeException exception) {
            details = CommitInsightDetails.empty();
        }
        return new CommitInsightDraft(
                entity.getStatus(),
                entity.getCategory(),
                entity.getRiskLevel(),
                entity.getSummary(),
                details
        );
    }
}
