package com.ivan_naumenko.repository_analyzer.commit.service;

import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LoggingCommitProcessor implements CommitProcessor {

    private static final Logger log = LoggerFactory.getLogger(
            LoggingCommitProcessor.class
    );

    private final RepositoryCommitRepository commitRepository;

    @Override
    public void process(Integer commitId) {
        Objects.requireNonNull(commitId, "commitId не должен быть null");

        RepositoryCommit commit = commitRepository.findById(commitId)
                .orElseThrow(() -> new IllegalStateException(
                        "Захваченный коммит " + commitId + " не найден"
                ));

        log.info(
                "Worker получил коммит {} с hash {}",
                commit.getId(),
                commit.getCommitHash()
        );
    }
}
