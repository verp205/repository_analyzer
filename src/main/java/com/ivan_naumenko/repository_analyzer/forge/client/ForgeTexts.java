package com.ivan_naumenko.repository_analyzer.forge.client;

import tools.jackson.databind.JsonNode;

final class ForgeTexts {

    private ForgeTexts() {
    }

    static String text(JsonNode node) {
        return node != null && node.isString() ? node.stringValue() : "";
    }

    static int number(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : 0;
    }

    /** Убирает HTML-комментарии шаблонов PR, лишние пробелы и обрезает текст. */
    static String compact(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String cleaned = value
                .replaceAll("(?s)<!--.*?-->", "")
                .replace("\r\n", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
        return cleaned.length() > maxLength
                ? cleaned.substring(0, maxLength).strip() + "…"
                : cleaned;
    }
}
