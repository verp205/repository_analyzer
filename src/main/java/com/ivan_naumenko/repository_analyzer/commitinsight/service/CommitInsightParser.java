package com.ivan_naumenko.repository_analyzer.commitinsight.service;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.ChangeCategory;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDetails;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDraft;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightStatus;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.RiskLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Терпимый к шуму разбор JSON-ответов модели: игнорирует рассуждения,
 * Markdown-обёртки и лишние поля, ограничивает размер текста.
 */
@Service
@RequiredArgsConstructor
public class CommitInsightParser {

    public static final int SHORT_HASH_LENGTH = 7;

    private static final Pattern THINK_BLOCK = Pattern.compile(
            "(?is)<think>.*?</think>"
    );
    private static final int MAX_TEXT_LENGTH = 600;
    private static final int MAX_ITEMS = 5;
    private static final int FALLBACK_SUMMARY_LENGTH = 400;

    private final ObjectMapper mapper;

    public Optional<CommitInsightDraft> parseSingle(String response) {
        return readObject(response).flatMap(this::toDraft);
    }

    public Map<String, CommitInsightDraft> parseBatch(String response) {
        Optional<JsonNode> root = readObject(response);
        if (root.isEmpty()) {
            return Map.of();
        }
        JsonNode commits = root.get().path("commits");
        if (!commits.isArray()) {
            return Map.of();
        }

        Map<String, CommitInsightDraft> result = new LinkedHashMap<>();
        for (int index = 0; index < commits.size(); index++) {
            JsonNode item = commits.path(index);
            String hash = text(item.path("hash")).toLowerCase(Locale.ROOT);
            if (hash.length() < SHORT_HASH_LENGTH) {
                continue;
            }
            String key = hash.substring(0, SHORT_HASH_LENGTH);
            toDraft(item).ifPresent(draft -> result.putIfAbsent(key, draft));
        }
        return result;
    }

    public String fallbackSummary(String response) {
        if (response == null) {
            return "";
        }
        String text = THINK_BLOCK.matcher(response)
                .replaceAll(" ")
                .replaceAll("[#*`>|{}\"\\[\\]]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        return truncate(text, FALLBACK_SUMMARY_LENGTH);
    }

    private Optional<JsonNode> readObject(String response) {
        if (response == null) {
            return Optional.empty();
        }
        String text = THINK_BLOCK.matcher(response).replaceAll("").strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Optional.empty();
        }
        try {
            JsonNode node = mapper.readTree(text.substring(start, end + 1));
            return node != null && node.isObject()
                    ? Optional.of(node)
                    : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<CommitInsightDraft> toDraft(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }
        String summary = text(node.path("summary"));
        if (summary.isBlank()) {
            return Optional.empty();
        }

        CommitInsightDetails details = new CommitInsightDetails(
                list(node.path("keyChanges")),
                text(node.path("behaviorImpact")),
                list(node.path("risks")),
                list(node.path("securityNotes")),
                text(node.path("testNotes")),
                list(node.path("checks"))
        );
        return Optional.of(new CommitInsightDraft(
                CommitInsightStatus.ANALYZED,
                ChangeCategory.parse(text(node.path("category"))),
                RiskLevel.parse(text(node.path("riskLevel"))),
                summary,
                details
        ));
    }

    private String text(JsonNode node) {
        if (node == null || !node.isString()) {
            return "";
        }
        return truncate(node.stringValue().strip(), MAX_TEXT_LENGTH);
    }

    private List<String> list(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        if (node.isString()) {
            String value = text(node);
            if (!value.isBlank()) {
                result.add(value);
            }
            return result;
        }
        if (!node.isArray()) {
            return result;
        }
        for (int index = 0;
             index < node.size() && result.size() < MAX_ITEMS;
             index++) {
            String value = text(node.path(index));
            if (!value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    private String truncate(String value, int maxLength) {
        return value.length() > maxLength
                ? value.substring(0, maxLength).strip() + "…"
                : value;
    }
}
