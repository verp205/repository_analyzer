package com.ivan_naumenko.repository_analyzer.llm.cache.repository;

import com.ivan_naumenko.repository_analyzer.llm.cache.entity.LlmCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmCacheRepository extends JpaRepository<LlmCacheEntry, String> {
}
