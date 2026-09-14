package com.ivan_naumenko.repository_analyzer.forge.service;

import com.ivan_naumenko.repository_analyzer.forge.client.ForgeClient;
import com.ivan_naumenko.repository_analyzer.forge.config.ForgeProperties;
import com.ivan_naumenko.repository_analyzer.forge.exception.ForgeRateLimitException;
import com.ivan_naumenko.repository_analyzer.forge.model.CommitPullRequestEntity;
import com.ivan_naumenko.repository_analyzer.forge.model.ForgeProvider;
import com.ivan_naumenko.repository_analyzer.forge.model.ForgeRepository;
import com.ivan_naumenko.repository_analyzer.forge.model.PullRequestContext;
import com.ivan_naumenko.repository_analyzer.forge.model.PullRequestLookupStatus;
import com.ivan_naumenko.repository_analyzer.forge.model.PullRequestSummary;
import com.ivan_naumenko.repository_analyzer.forge.repository.CommitPullRequestRepository;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Находит pull/merge request для коммита. Результат кэшируется в базе,
 * детали PR — в памяти (у одного PR обычно много коммитов). Любая ошибка
 * API не мешает анализу: коммит просто разбирается без контекста PR.
 */
@Service
public class ForgeContextService {

    private static final Logger log = LoggerFactory.getLogger(ForgeContextService.class);
    private static final int MAX_CACHED_PULL_REQUESTS = 2_000;

    private final ForgeProperties properties;
    private final ForgeRepositoryResolver resolver;
    private final Map<ForgeProvider, ForgeClient> clients = new EnumMap<>(ForgeProvider.class);
    private final CommitPullRequestRepository repository;
    private final SqliteWriteCoordinator writeCoordinator;
    private final ObjectMapper mapper;
    private final Map<String, PullRequestContext> detailsCache = new ConcurrentHashMap<>();
    private final Map<String, Instant> pausedUntil = new ConcurrentHashMap<>();

    public ForgeContextService(
            ForgeProperties properties,
            ForgeRepositoryResolver resolver,
            List<ForgeClient> clients,
            CommitPullRequestRepository repository,
            SqliteWriteCoordinator writeCoordinator,
            ObjectMapper mapper
    ) {
        this.properties = properties;
        this.resolver = resolver;
        clients.forEach(client -> this.clients.put(client.provider(), client));
        this.repository = repository;
        this.writeCoordinator = writeCoordinator;
        this.mapper = mapper;
    }

    public Optional<PullRequestContext> findForCommit(
            String repositoryUrl,
            Integer commitId,
            String commitHash
    ) {
        if (!properties.enabled() || commitId == null || commitHash == null) {
            return Optional.empty();
        }
        Optional<ForgeRepository> forgeRepository = resolver.resolve(repositoryUrl);
        if (forgeRepository.isEmpty()) {
            return Optional.empty();
        }

        Optional<CommitPullRequestEntity> cached = repository.findById(commitId);
        if (cached.isPresent()) {
            return fromCache(cached.get());
        }

        ForgeRepository target = forgeRepository.get();
        ForgeClient client = clients.get(target.provider());
        if (client == null || isPaused(target)) {
            return Optional.empty();
        }

        try {
            Optional<PullRequestSummary> summary = client.findPullRequestForCommit(target, commitHash);
            Optional<PullRequestContext> context = summary.map(found -> detailsCache.computeIfAbsent(
                    target.cacheKey() + "#" + found.number(),
                    ignored -> client.loadDetails(target, found)
            ));
            trimCache();
            store(commitId, context);
            return context;
        } catch (ForgeRateLimitException exception) {
            pausedUntil.put(target.apiBaseUrl(), exception.retryAt());
            log.warn(
                    "{} Контекст pull request'ов не загружается до {}. "
                            + "Укажите токен, чтобы увеличить лимит.",
                    exception.getMessage(),
                    exception.retryAt()
            );
            return Optional.empty();
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn(
                    "Не удалось получить pull request для коммита {}: {}",
                    commitHash,
                    exception.getMessage()
            );
            return Optional.empty();
        }
    }

    private Optional<PullRequestContext> fromCache(CommitPullRequestEntity entity) {
        if (entity.getStatus() != PullRequestLookupStatus.FOUND || entity.getContextJson() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(entity.getContextJson(), PullRequestContext.class));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private boolean isPaused(ForgeRepository repository) {
        Instant until = pausedUntil.get(repository.apiBaseUrl());
        if (until == null) {
            return false;
        }
        if (Instant.now().isAfter(until)) {
            pausedUntil.remove(repository.apiBaseUrl(), until);
            return false;
        }
        return true;
    }

    private void store(Integer commitId, Optional<PullRequestContext> context) {
        CommitPullRequestEntity entity = new CommitPullRequestEntity();
        entity.setCommitId(commitId);
        entity.setStatus(context.isPresent()
                ? PullRequestLookupStatus.FOUND
                : PullRequestLookupStatus.NONE);
        entity.setContextJson(context.map(mapper::writeValueAsString).orElse(null));
        entity.setFetchedAt(LocalDateTime.now());
        writeCoordinator.execute(() -> repository.save(entity));
    }

    private void trimCache() {
        if (detailsCache.size() > MAX_CACHED_PULL_REQUESTS) {
            detailsCache.clear();
        }
    }
}
