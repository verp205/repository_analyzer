package com.ivan_naumenko.repository_analyzer.llm.cache.service;

import com.ivan_naumenko.repository_analyzer.llm.cache.entity.LlmCacheEntry;
import com.ivan_naumenko.repository_analyzer.llm.cache.repository.LlmCacheRepository;
import com.ivan_naumenko.repository_analyzer.llm.prompt.service.PromptHashService;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LlmCacheService {

    private static final Pattern SHA_256_HEX = Pattern.compile(
            "[0-9a-f]{64}"
    );

    private final LlmCacheRepository repository;
    private final PromptHashService promptHashService;

    @Transactional(readOnly = true)
    public Optional<String> findResponse(String promptHash) {

        validateHash(promptHash);

        return repository.findById(promptHash)
                .map(LlmCacheEntry::getResponseText);
    }

    @Transactional
    public void discardResponse(String promptHash, String rejectedResponse) {
        validateHash(promptHash);
        repository.findById(promptHash).filter(entry -> entry.getResponseText().equals(rejectedResponse))
                .ifPresent(repository::delete);
    }

    @Transactional
    public String saveResponse(
            String promptHash,
            String cacheNamespace,
            String prompt,
            String response,
            ReportAudience audience
    ) {
        validateHash(promptHash);
        validateText(cacheNamespace, "cacheNamespace");
        validateText(prompt, "prompt");
        validateText(response, "response");

        String calculatedHash = promptHashService.calculate(
                cacheNamespace + "\n" + prompt
        );

        if (!promptHash.equals(calculatedHash)) {
            throw new IllegalArgumentException(
                    "promptHash не соответствует провайдеру и prompt"
            );
        }

        Optional<LlmCacheEntry> existing = repository.findById(promptHash);

        if (existing.isPresent()) {
            return responseFromExisting(
                    existing.get(),
                    prompt,
                    audience
            );
        }

        LlmCacheEntry entry = new LlmCacheEntry();
        entry.setPromptHash(promptHash);
        entry.setPromptText(prompt);
        entry.setResponseText(response);
        entry.setAudience(audience);
        entry.setCreatedAt(LocalDateTime.now());

        repository.saveAndFlush(entry);

        return response;
    }

    private String responseFromExisting(
            LlmCacheEntry existing,
            String prompt,
            ReportAudience audience
    ) {
        if (!existing.getPromptText().equals(prompt)) {
            throw new IllegalStateException(
                    "В кэше найден другой prompt с тем же hash"
            );
        }
        if (!Objects.equals(existing.getAudience(), audience)) {
            throw new IllegalStateException(
                    "В кэше найден prompt с другой аудиторией отчёта"
            );
        }

        return existing.getResponseText();
    }

    private void validateHash(String promptHash) {
        if (promptHash == null
                || !SHA_256_HEX.matcher(promptHash).matches()) {
            throw new IllegalArgumentException(
                    "promptHash должен быть SHA-256 в lowercase hex"
            );
        }
    }

    private void validateText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " не должен быть пустым"
            );
        }
    }
}
