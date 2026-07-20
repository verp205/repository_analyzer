package com.ivan_naumenko.repository_analyzer.javaanalysis.service;

import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaMethodAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaTypeAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaTypeKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaAnalysisServiceTest {

    private JavaAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new JavaAnalysisService();
    }

    @Test
    void extractsPackageImportsClassMethodsAndNestedTypes() {
        String source = """
                package example.analysis;

                import java.util.List;
                import static java.util.Collections.emptyList;

                @Deprecated
                public final class Sample {

                    public Sample() {
                    }

                    @Override
                    public String value(int number, String... values) {
                        return values[number];
                    }

                    class Nested {
                        void nestedMethod() {
                        }
                    }
                }
                """;

        JavaFileAnalysis result = service.analyze(
                "src/main/java/example/analysis/Sample.java",
                source
        );

        assertTrue(result.parsingSuccessful());
        assertTrue(result.problems().isEmpty());
        assertEquals("example.analysis", result.packageName());
        assertEquals(
                List.of(
                        "java.util.List",
                        "static java.util.Collections.emptyList"
                ),
                result.imports()
        );
        assertEquals(2, result.types().size());

        JavaTypeAnalysis sample = findType(result, "Sample");
        assertEquals(JavaTypeKind.CLASS, sample.kind());
        assertEquals(List.of("public", "final"), sample.modifiers());
        assertEquals(List.of("Deprecated"), sample.annotations());
        assertEquals(1, sample.constructorCount());
        assertEquals(1, sample.methods().size());

        JavaMethodAnalysis value = sample.methods().getFirst();
        assertEquals("value", value.name());
        assertEquals("String", value.returnType());
        assertEquals(List.of("int", "String..."), value.parameterTypes());
        assertEquals(List.of("public"), value.modifiers());
        assertEquals(List.of("Override"), value.annotations());

        JavaTypeAnalysis nested = findType(result, "Nested");
        assertEquals(JavaTypeKind.CLASS, nested.kind());
        assertEquals(List.of("nestedMethod"), nested.methods()
                .stream()
                .map(JavaMethodAnalysis::name)
                .toList());
    }

    @Test
    void recognizesInterfaceEnumRecordAndAnnotation() {
        String source = """
                interface Service {
                    void execute();
                }

                enum State {
                    NEW, DONE
                }

                record User(String name) {
                    User {
                        name = name.trim();
                    }
                }

                @interface Marker {
                    String value();
                }
                """;

        JavaFileAnalysis result = service.analyze("Types.java", source);

        assertTrue(result.parsingSuccessful());
        assertEquals(JavaTypeKind.INTERFACE, findType(result, "Service").kind());
        assertEquals(JavaTypeKind.ENUM, findType(result, "State").kind());

        JavaTypeAnalysis user = findType(result, "User");
        assertEquals(JavaTypeKind.RECORD, user.kind());
        assertEquals(1, user.constructorCount());

        JavaTypeAnalysis marker = findType(result, "Marker");
        assertEquals(JavaTypeKind.ANNOTATION, marker.kind());
        assertEquals(1, marker.methods().size());
        assertEquals("value", marker.methods().getFirst().name());
        assertEquals("String", marker.methods().getFirst().returnType());
    }

    @Test
    void returnsProblemsForInvalidJavaSource() {
        JavaFileAnalysis result = service.analyze(
                "Broken.java",
                "class Broken { void test( }"
        );

        assertFalse(result.parsingSuccessful());
        assertFalse(result.problems().isEmpty());
        assertTrue(result.types().isEmpty());
        assertTrue(result.imports().isEmpty());
    }

    @Test
    void acceptsEmptyCompilationUnit() {
        JavaFileAnalysis result = service.analyze("Empty.java", "");

        assertTrue(result.parsingSuccessful());
        assertEquals("", result.packageName());
        assertTrue(result.types().isEmpty());
        assertEquals(0, result.lineCount());
    }

    @Test
    void validatesInputArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.analyze(" ", "class Sample {}")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.analyze("Sample.java", null)
        );
    }

    private JavaTypeAnalysis findType(
            JavaFileAnalysis result,
            String name
    ) {
        return result.types()
                .stream()
                .filter(type -> type.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
