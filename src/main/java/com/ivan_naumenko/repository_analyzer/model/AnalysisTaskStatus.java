package com.ivan_naumenko.repository_analyzer.model;

public enum AnalysisTaskStatus {
    NEW,
    CLONING,
    DISCOVERING_COMMITS,
    PROCESSING,
    COMPLETED,
    FAILED
}
