package com.ivan_naumenko.repository_analyzer.llm.cache.entity;

import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "llm_cache")
public class LlmCacheEntry {

    @Id
    @Column(name = "prompt_hash", nullable = false, length = 64)
    private String promptHash;

    @Column(name = "prompt_text", nullable = false)
    private String promptText;

    @Column(name = "response_text", nullable = false)
    private String responseText;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience")
    private ReportAudience audience;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
