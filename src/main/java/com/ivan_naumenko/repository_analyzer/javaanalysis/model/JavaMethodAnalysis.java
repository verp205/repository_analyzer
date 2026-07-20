package com.ivan_naumenko.repository_analyzer.javaanalysis.model;

import java.util.List;

public record JavaMethodAnalysis(
        String name,
        String returnType,
        List<String> parameterTypes,
        List<String> modifiers,
        List<String> annotations
) {

    public JavaMethodAnalysis {
        parameterTypes = List.copyOf(parameterTypes);
        modifiers = List.copyOf(modifiers);
        annotations = List.copyOf(annotations);
    }
}
