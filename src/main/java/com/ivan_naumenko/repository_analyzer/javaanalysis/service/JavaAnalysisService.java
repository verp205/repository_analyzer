package com.ivan_naumenko.repository_analyzer.javaanalysis.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaMethodAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaTypeAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaTypeKind;
import com.ivan_naumenko.repository_analyzer.cache.BoundedSingleFlightCache;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JavaAnalysisService {
    private static final int MAX_CACHED_SOURCE_CHARS = 256 * 1024;
    private final BoundedSingleFlightCache<String, JavaFileAnalysis> analyses = new BoundedSingleFlightCache<>(64);

    public JavaFileAnalysis analyze(
            String filePath,
            String sourceCode
    ) {
        validateArguments(filePath, sourceCode);
        if (sourceCode.length() > MAX_CACHED_SOURCE_CHARS) return parse(filePath, sourceCode);
        // Exact source text is the key: no hash collisions, no sharing mutable parser/AST instances.
        JavaFileAnalysis parsed = analyses.get(sourceCode, () -> parse("(source)", sourceCode));
        return new JavaFileAnalysis(filePath, parsed.packageName(), parsed.imports(), parsed.types(), parsed.lineCount(),
                parsed.parsingSuccessful(), parsed.problems());
    }

    private JavaFileAnalysis parse(String filePath, String sourceCode) {
        JavaParser javaParser = new JavaParser(
                new ParserConfiguration().setLanguageLevel(
                        ParserConfiguration.LanguageLevel.BLEEDING_EDGE
                )
        );
        ParseResult<CompilationUnit> parseResult = javaParser.parse(sourceCode);
        List<String> problems = parseResult.getProblems()
                .stream()
                .map(Problem::getMessage)
                .toList();

        if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
            return new JavaFileAnalysis(
                    filePath,
                    "",
                    List.of(),
                    List.of(),
                    lineCount(sourceCode),
                    false,
                    problems
            );
        }

        CompilationUnit compilationUnit = parseResult.getResult().orElseThrow();

        return new JavaFileAnalysis(
                filePath,
                packageName(compilationUnit),
                imports(compilationUnit),
                types(compilationUnit),
                lineCount(sourceCode),
                true,
                problems
        );
    }

    private String packageName(CompilationUnit compilationUnit) {
        return compilationUnit.getPackageDeclaration()
                .map(NodeWithName::getNameAsString)
                .orElse("");
    }

    private List<String> imports(CompilationUnit compilationUnit) {
        return compilationUnit.getImports()
                .stream()
                .map(declaration -> {
                    String staticPrefix = declaration.isStatic()
                            ? "static "
                            : "";
                    String wildcardSuffix = declaration.isAsterisk()
                            ? ".*"
                            : "";
                    return staticPrefix
                            + declaration.getNameAsString()
                            + wildcardSuffix;
                })
                .toList();
    }

    private List<JavaTypeAnalysis> types(CompilationUnit compilationUnit) {
        return compilationUnit.findAll(TypeDeclaration.class)
                .stream()
                .map(this::toTypeAnalysis)
                .toList();
    }

    private JavaTypeAnalysis toTypeAnalysis(TypeDeclaration<?> declaration) {
        List<JavaMethodAnalysis> methods = declaration.getMembers()
                .stream()
                .filter(member -> member instanceof MethodDeclaration
                        || member instanceof AnnotationMemberDeclaration)
                .map(member -> {
                    if (member instanceof MethodDeclaration method) {
                        return toMethodAnalysis(method);
                    }
                    return toAnnotationMethodAnalysis(
                            (AnnotationMemberDeclaration) member
                    );
                })
                .toList();

        int constructorCount = (int) declaration.getMembers()
                .stream()
                .filter(member -> member instanceof ConstructorDeclaration
                        || member instanceof CompactConstructorDeclaration)
                .count();

        return new JavaTypeAnalysis(
                declaration.getNameAsString(),
                typeKind(declaration),
                modifiers(declaration),
                annotations(declaration.getAnnotations()),
                methods,
                constructorCount
        );
    }

    private JavaMethodAnalysis toMethodAnalysis(MethodDeclaration method) {
        List<String> parameterTypes = method.getParameters()
                .stream()
                .map(parameter -> parameter.getTypeAsString()
                        + (parameter.isVarArgs() ? "..." : ""))
                .toList();

        return new JavaMethodAnalysis(
                method.getNameAsString(),
                method.getTypeAsString(),
                parameterTypes,
                modifiers(method),
                annotations(method.getAnnotations())
        );
    }

    private JavaMethodAnalysis toAnnotationMethodAnalysis(
            AnnotationMemberDeclaration method
    ) {
        return new JavaMethodAnalysis(
                method.getNameAsString(),
                method.getType().asString(),
                List.of(),
                List.of(),
                annotations(method.getAnnotations())
        );
    }

    private JavaTypeKind typeKind(TypeDeclaration<?> declaration) {
        if (declaration instanceof ClassOrInterfaceDeclaration classOrInterface) {
            return classOrInterface.isInterface()
                    ? JavaTypeKind.INTERFACE
                    : JavaTypeKind.CLASS;
        }
        if (declaration instanceof EnumDeclaration) {
            return JavaTypeKind.ENUM;
        }
        if (declaration instanceof RecordDeclaration) {
            return JavaTypeKind.RECORD;
        }
        if (declaration instanceof AnnotationDeclaration) {
            return JavaTypeKind.ANNOTATION;
        }

        throw new IllegalArgumentException(
                "Неподдерживаемый тип объявления: "
                        + declaration.getClass().getSimpleName()
        );
    }

    private List<String> modifiers(NodeWithModifiers<?> declaration) {
        return declaration.getModifiers()
                .stream()
                .map(Modifier::getKeyword)
                .map(Modifier.Keyword::asString)
                .toList();
    }

    private List<String> annotations(List<AnnotationExpr> annotations) {
        return annotations.stream()
                .map(AnnotationExpr::getNameAsString)
                .toList();
    }

    private int lineCount(String sourceCode) {
        if (sourceCode.isEmpty()) {
            return 0;
        }
        return (int) sourceCode.lines().count();
    }

    private void validateArguments(
            String filePath,
            String sourceCode
    ) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException(
                    "Путь к Java-файлу не должен быть пустым"
            );
        }

        if (sourceCode == null) {
            throw new IllegalArgumentException(
                    "Исходный Java-код не должен быть null"
            );
        }
    }
}
