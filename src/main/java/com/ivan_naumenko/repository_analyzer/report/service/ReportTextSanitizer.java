package com.ivan_naumenko.repository_analyzer.report.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Приводит Markdown-ответ модели к формату отчёта: убирает рассуждения,
 * внешнюю обёртку в блок кода, заголовки первого уровня (заголовок отчёта
 * формирует система) и превращает таблицы в списки.
 */
@Service
public class ReportTextSanitizer {

    private static final Pattern THINK_BLOCK = Pattern.compile(
            "(?is)<think>.*?</think>"
    );
    private static final Pattern OUTER_FENCE = Pattern.compile(
            "(?s)^```[a-zA-Z]*\\R(.*)\\R```$"
    );

    public String sanitize(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Текст LLM-ответа не должен быть пустым");
        }

        String cleaned = THINK_BLOCK.matcher(text).replaceAll("").strip();
        cleaned = OUTER_FENCE.matcher(cleaned).replaceFirst("$1").strip();
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Текст LLM-ответа не должен быть пустым");
        }

        List<String> result = new ArrayList<>();
        boolean fencedCode = false;

        for (String line : cleaned.split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                fencedCode = !fencedCode;
                result.add(line);
                continue;
            }

            if (!fencedCode && trimmed.startsWith("# ")) {
                continue;
            }
            if (!fencedCode && isTableSeparator(trimmed)) {
                continue;
            }
            if (!fencedCode && isMarkdownTableRow(trimmed)) {
                result.add(toListItem(trimmed));
                continue;
            }
            result.add(line);
        }

        List<String> compact = removeEmptySections(result);
        return String.join(
                "\n",
                compact.stream().anyMatch(line -> !line.isBlank()) ? compact : result
        ).strip();
    }

    /**
     * Модель иногда оставляет заголовок без текста или повторяет пункт в
     * одном разделе. Такие места только занимают место в документе.
     */
    private List<String> removeEmptySections(List<String> lines) {
        List<String> output = new ArrayList<>();
        List<String> section = new ArrayList<>();
        Set<String> seenItems = new HashSet<>();
        boolean fencedCode = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                fencedCode = !fencedCode;
            }
            if (!fencedCode && trimmed.startsWith("## ")) {
                flushSection(output, section);
                section = new ArrayList<>();
                seenItems.clear();
            } else if (!fencedCode && trimmed.startsWith("- ") && !seenItems.add(trimmed)) {
                continue;
            }
            section.add(line);
        }
        flushSection(output, section);
        return output;
    }

    private void flushSection(List<String> output, List<String> section) {
        boolean heading = !section.isEmpty() && section.getFirst().trim().startsWith("## ");
        boolean empty = section.stream().skip(heading ? 1 : 0).allMatch(String::isBlank);
        if (!(heading && empty)) {
            output.addAll(section);
        }
    }

    private boolean isMarkdownTableRow(String line) {
        return line.length() > 2
                && line.startsWith("|")
                && line.endsWith("|")
                && line.indexOf('|', 1) > 0;
    }

    private boolean isTableSeparator(String line) {
        if (!isMarkdownTableRow(line)) {
            return false;
        }
        return Arrays.stream(line.substring(1, line.length() - 1).split("\\|"))
                .map(String::trim)
                .allMatch(cell -> cell.matches(":?-{3,}:?"));
    }

    private String toListItem(String line) {
        List<String> cells = Arrays.stream(
                        line.substring(1, line.length() - 1).split("\\|")
                )
                .map(String::trim)
                .filter(cell -> !cell.isEmpty())
                .toList();
        return "- " + String.join("; ", cells);
    }
}
