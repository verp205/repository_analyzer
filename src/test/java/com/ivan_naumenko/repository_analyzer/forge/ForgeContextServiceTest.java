package com.ivan_naumenko.repository_analyzer.forge;

import com.ivan_naumenko.repository_analyzer.forge.client.ForgeClient;
import com.ivan_naumenko.repository_analyzer.forge.config.ForgeProperties;
import com.ivan_naumenko.repository_analyzer.forge.exception.ForgeRateLimitException;
import com.ivan_naumenko.repository_analyzer.forge.model.CommitPullRequestEntity;
import com.ivan_naumenko.repository_analyzer.forge.model.ForgeProvider;
import com.ivan_naumenko.repository_analyzer.forge.model.PullRequestContext;
import com.ivan_naumenko.repository_analyzer.forge.model.PullRequestLookupStatus;
import com.ivan_naumenko.repository_analyzer.forge.model.PullRequestSummary;
import com.ivan_naumenko.repository_analyzer.forge.repository.CommitPullRequestRepository;
import com.ivan_naumenko.repository_analyzer.forge.service.ForgeContextService;
import com.ivan_naumenko.repository_analyzer.forge.service.ForgeRepositoryResolver;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForgeContextServiceTest {

    private static final String GITHUB_URL = "https://github.com/o/r.git";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Mock
    private ForgeClient client;

    @Mock
    private CommitPullRequestRepository repository;

    private ForgeContextService service;

    @BeforeEach
    void setUp() {
        when(client.provider()).thenReturn(ForgeProvider.GITHUB);
        ForgeProperties properties = new ForgeProperties(
                true, "", "https://api.github.com", "", List.of(), 5_000, 8, 3, 1_500
        );
        service = new ForgeContextService(
                properties,
                new ForgeRepositoryResolver(properties),
                List.of(client),
                repository,
                new SqliteWriteCoordinator(),
                mapper
        );
    }

    @Test
    void loadsPullRequestOnceAndCachesResult() {
        PullRequestSummary summary = new PullRequestSummary(12, "Оплата", "Описание", "url");
        when(client.findPullRequestForCommit(any(), eq("aaa"))).thenReturn(Optional.of(summary));
        when(client.findPullRequestForCommit(any(), eq("bbb"))).thenReturn(Optional.of(summary));
        when(client.loadDetails(any(), eq(summary))).thenReturn(context());

        assertEquals("Оплата", service.findForCommit(GITHUB_URL, 1, "aaa").orElseThrow().title());
        assertEquals("Оплата", service.findForCommit(GITHUB_URL, 2, "bbb").orElseThrow().title());

        verify(client, times(1)).loadDetails(any(), eq(summary));
        ArgumentCaptor<CommitPullRequestEntity> saved = ArgumentCaptor.forClass(CommitPullRequestEntity.class);
        verify(repository, times(2)).save(saved.capture());
        assertEquals(PullRequestLookupStatus.FOUND, saved.getValue().getStatus());
        assertTrue(saved.getValue().getContextJson().contains("Оплата"));
    }

    @Test
    void returnsCachedContextWithoutCallingApi() {
        CommitPullRequestEntity cached = new CommitPullRequestEntity();
        cached.setCommitId(1);
        cached.setStatus(PullRequestLookupStatus.FOUND);
        cached.setContextJson(mapper.writeValueAsString(context()));
        cached.setFetchedAt(LocalDateTime.now());
        when(repository.findById(1)).thenReturn(Optional.of(cached));

        assertEquals(12, service.findForCommit(GITHUB_URL, 1, "aaa").orElseThrow().number());
        verify(client, never()).findPullRequestForCommit(any(), anyString());
    }

    @Test
    void pausesRequestsAfterRateLimit() {
        when(client.findPullRequestForCommit(any(), anyString()))
                .thenThrow(new ForgeRateLimitException("лимит", Instant.now().plusSeconds(3_600)));

        assertTrue(service.findForCommit(GITHUB_URL, 1, "aaa").isEmpty());
        assertTrue(service.findForCommit(GITHUB_URL, 2, "bbb").isEmpty());

        verify(client, times(1)).findPullRequestForCommit(any(), anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void ignoresRepositoriesOutsideSupportedHostings() {
        assertTrue(service.findForCommit("https://example.com/project.git", 1, "aaa").isEmpty());

        verifyNoInteractions(repository);
    }

    private PullRequestContext context() {
        return new PullRequestContext(ForgeProvider.GITHUB, 12, "Оплата", "url", "Описание", List.of(), List.of());
    }
}
