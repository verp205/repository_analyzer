package com.ivan_naumenko.repository_analyzer.report.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ReportTextSanitizer {

    public String sanitize(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Текст LLM-ответа не должен быть пустым");
        }

        List<String> result = new ArrayList<>();
        boolean fencedCode = false;

        for (String line : text.strip().split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                fencedCode = !fencedCode;
                result.add(line);
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

        return String.join("\n", result).strip();
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
