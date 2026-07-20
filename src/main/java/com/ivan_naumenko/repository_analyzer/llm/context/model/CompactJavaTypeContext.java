package com.ivan_naumenko.repository_analyzer.llm.context.model;

import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaTypeKind;

import java.util.List;
import java.util.Objects;

public record CompactJavaTypeContext(
        String name,
        JavaTypeKind kind,
        List<String> modifiers,
        List<String> annotations,
        int constructorCount,
        List<CompactJavaMethodContext> methods,
        int omittedMethodCount
) {

    public CompactJavaTypeContext {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Имя Java-типа не должно быть пустым"
            );
        }
        if (constructorCount < 0 || omittedMethodCount < 0) {
            throw new IllegalArgumentException(
                    "Количество элементов Java-типа не может быть отрицательным"
            );
        }

        kind = Objects.requireNonNull(kind, "kind не должен быть null");
        modifiers = copy(modifiers, "modifiers");
        annotations = copy(annotations, "annotations");
        methods = copy(methods, "methods");
    }

    private static <T> List<T> copy(List<T> values, String fieldName) {
        return List.copyOf(Objects.requireNonNull(
                values,
                fieldName + " не должен быть null"
        ));
    }
}
