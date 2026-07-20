package com.ivan_naumenko.repository_analyzer.javaanalysis.model;

import java.util.List;

public record JavaTypeAnalysis(
        String name,
        JavaTypeKind kind,
        List<String> modifiers,
        List<String> annotations,
        List<JavaMethodAnalysis> methods,
        int constructorCount
) {

    public JavaTypeAnalysis {
        modifiers = List.copyOf(modifiers);
        annotations = List.copyOf(annotations);
        methods = List.copyOf(methods);
    }
}
