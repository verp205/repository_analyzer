package com.ivan_naumenko.repository_analyzer.javaanalysis.model;

import java.util.Objects;

public record ChangedJavaFileAnalysis(
        int filePosition,
        JavaFileAnalysis analysis
) {

    public ChangedJavaFileAnalysis {
        if (filePosition < 0) {
            throw new IllegalArgumentException(
                    "Позиция изменённого файла не может быть отрицательной"
            );
        }
        analysis = Objects.requireNonNull(
                analysis,
                "analysis не должен быть null"
        );
    }
}
