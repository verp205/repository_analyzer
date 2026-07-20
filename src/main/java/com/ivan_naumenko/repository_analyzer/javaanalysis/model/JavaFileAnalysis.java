package com.ivan_naumenko.repository_analyzer.javaanalysis.model;

import java.util.List;

public record JavaFileAnalysis(
        String filePath,
        String packageName,
        List<String> imports,
        List<JavaTypeAnalysis> types,
        int lineCount,
        boolean parsingSuccessful,
        List<String> problems
) {

    public JavaFileAnalysis {
        imports = List.copyOf(imports);
        types = List.copyOf(types);
        problems = List.copyOf(problems);
    }
}
