package com.ivan_naumenko.repository_analyzer.llm.context.model;

import java.util.List;
import java.util.Objects;

public record CompactJavaFileContext(
        String packageName,
        List<String> imports,
        int omittedImportCount,
        int lineCount,
        boolean parsingSuccessful,
        List<String> parsingProblems,
        int omittedParsingProblemCount,
        List<CompactJavaTypeContext> types,
        int omittedTypeCount
) {

    public CompactJavaFileContext {
        if (lineCount < 0
                || omittedImportCount < 0
                || omittedParsingProblemCount < 0
                || omittedTypeCount < 0) {
            throw new IllegalArgumentException(
                    "Счётчики Java-контекста не могут быть отрицательными"
            );
        }

        packageName = Objects.requireNonNullElse(packageName, "");
        imports = copy(imports, "imports");
        parsingProblems = copy(parsingProblems, "parsingProblems");
        types = copy(types, "types");
    }

    private static <T> List<T> copy(List<T> values, String fieldName) {
        return List.copyOf(Objects.requireNonNull(
                values,
                fieldName + " не должен быть null"
        ));
    }
}
