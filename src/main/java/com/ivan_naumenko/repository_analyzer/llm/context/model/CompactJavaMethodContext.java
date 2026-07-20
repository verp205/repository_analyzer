package com.ivan_naumenko.repository_analyzer.llm.context.model;

import java.util.List;
import java.util.Objects;

public record CompactJavaMethodContext(
        String name,
        String returnType,
        List<String> parameterTypes,
        List<String> modifiers,
        List<String> annotations
) {

    public CompactJavaMethodContext {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Имя Java-метода не должно быть пустым"
            );
        }

        returnType = Objects.requireNonNullElse(returnType, "");
        parameterTypes = copy(parameterTypes, "parameterTypes");
        modifiers = copy(modifiers, "modifiers");
        annotations = copy(annotations, "annotations");
    }

    private static <T> List<T> copy(List<T> values, String fieldName) {
        return List.copyOf(Objects.requireNonNull(
                values,
                fieldName + " не должен быть null"
        ));
    }
}
