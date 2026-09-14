package com.ivan_naumenko.repository_analyzer.forge.client;

import com.ivan_naumenko.repository_analyzer.forge.model.ForgeProvider;
import com.ivan_naumenko.repository_analyzer.forge.model.ForgeRepository;
import com.ivan_naumenko.repository_analyzer.forge.model.PullRequestContext;
import com.ivan_naumenko.repository_analyzer.forge.model.PullRequestSummary;

import java.util.Optional;

public interface ForgeClient {

    ForgeProvider provider();

    /** Pull/merge request, в который входит коммит (предпочтительно слитый). */
    Optional<PullRequestSummary> findPullRequestForCommit(ForgeRepository repository, String commitHash);

    /** Обсуждение и связанные задачи. */
    PullRequestContext loadDetails(ForgeRepository repository, PullRequestSummary summary);
}
