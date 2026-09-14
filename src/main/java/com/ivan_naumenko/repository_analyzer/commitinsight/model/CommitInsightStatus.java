package com.ivan_naumenko.repository_analyzer.commitinsight.model;

public enum CommitInsightStatus {
    /** Структурированный ответ LLM. */
    ANALYZED,
    /** Сформирован системой без LLM: слияние, пустой коммит, lock-файлы. */
    TEMPLATE,
    /** LLM ответила, но не в формате JSON — сохранено краткое описание. */
    UNSTRUCTURED,
    /** LLM недоступна; коммит будет проанализирован при повторе отчёта. */
    FAILED
}
