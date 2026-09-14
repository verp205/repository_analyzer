package com.ivan_naumenko.repository_analyzer.llm.context.service;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingData;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFile;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiff;
import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.ChangedJavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaMethodAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaTypeAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaTypeKind;
import com.ivan_naumenko.repository_analyzer.llm.context.config.ContextProperties;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactCommitContext;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactFileContext;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactJavaFileContext;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactJavaTypeContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactContextServiceTest {

    private static final String COMMIT_HASH = "a".repeat(40);
    private static final String JAVA_PATH = "src/main/java/Sample.java";

    @Test
    void buildsContextAndMatchesJavaAnalysisByFilePosition() {
        CompactContextService service = service(50, 4_000, 50);
        ChangedFile readme = changedFile(
                "README.md",
                FileChangeType.MODIFY,
                false,
                1,
                1,
                "readme patch"
        );
        ChangedFile javaFile = changedFile(
                JAVA_PATH,
                FileChangeType.MODIFY,
                true,
                5,
                2,
                "java patch"
        );
        CommitDiff diff = diff(List.of(readme, javaFile));
        JavaFileAnalysis javaAnalysis = javaAnalysis(List.of(method("save")));

        CompactCommitContext result = service.build(
                commit(COMMIT_HASH),
                diff,
                List.of(new ChangedJavaFileAnalysis(1, javaAnalysis))
        );

        assertEquals(COMMIT_HASH, result.commitHash());
        assertEquals("Test commit", result.message());
        assertEquals("Test User", result.authorName());
        assertEquals(6, result.totalAddedLines());
        assertEquals(3, result.totalDeletedLines());
        assertEquals(0, result.omittedFileCount());
        assertEquals(2, result.files().size());

        CompactFileContext compactJavaFile = result.files().get(0);
        assertEquals(JAVA_PATH, compactJavaFile.path());
        assertEquals("example", compactJavaFile.javaAnalysis().packageName());
        assertEquals(
                List.of("java.util.List"),
                compactJavaFile.javaAnalysis().imports()
        );
        assertEquals(
                "Sample",
                compactJavaFile.javaAnalysis().types().getFirst().name()
        );
        assertEquals(
                "save",
                compactJavaFile.javaAnalysis()
                        .types()
                        .getFirst()
                        .methods()
                        .getFirst()
                        .name()
        );

        CompactFileContext compactReadme = result.files().get(1);
        assertEquals("README.md", compactReadme.path());
        assertNull(compactReadme.javaAnalysis());
        assertFalse(compactReadme.patchTruncated());
    }

    @Test
    void appliesFilePatchAndMethodLimits() {
        CompactContextService service = service(1, 5, 1);
        ChangedFile javaFile = changedFile(
                JAVA_PATH,
                FileChangeType.MODIFY,
                true,
                3,
                0,
                "123456789"
        );
        ChangedFile secondFile = changedFile(
                "notes.txt",
                FileChangeType.MODIFY,
                false,
                1,
                0,
                "property"
        );
        JavaFileAnalysis analysis = javaAnalysis(List.of(
                method("first"),
                method("second")
        ));

        CompactCommitContext result = service.build(
                commit(COMMIT_HASH),
                diff(List.of(javaFile, secondFile)),
                List.of(new ChangedJavaFileAnalysis(0, analysis))
        );

        assertEquals(1, result.files().size());
        assertEquals(1, result.omittedFileCount());
        assertEquals("12345", result.files().getFirst().patch());
        assertTrue(result.files().getFirst().patchTruncated());

        CompactJavaTypeContext type = result.files()
                .getFirst()
                .javaAnalysis()
                .types()
                .getFirst();
        assertEquals(1, type.methods().size());
        assertEquals("first", type.methods().getFirst().name());
        assertEquals(1, type.omittedMethodCount());
    }

    @Test
    void sharesTotalPatchBudgetAcrossAllIncludedFiles() {
        CompactContextService service = new CompactContextService(
                new ContextProperties(2, 10, 12, 10, 5, 5, 5)
        );
        List<ChangedFile> files = List.of(
                changedFile(
                        "first.txt",
                        FileChangeType.MODIFY,
                        false,
                        1,
                        0,
                        "1234567890"
                ),
                changedFile(
                        "second.txt",
                        FileChangeType.MODIFY,
                        false,
                        1,
                        0,
                        "abcdefghij"
                )
        );

        CompactCommitContext result = service.build(
                commit(COMMIT_HASH),
                diff(files),
                List.of()
        );

        assertEquals(12, result.files().stream()
                .mapToInt(file -> file.patch().length())
                .sum());
        assertEquals("123456", result.files().get(0).patch());
        assertEquals("abcdef", result.files().get(1).patch());
        assertTrue(result.files().stream().allMatch(
                CompactFileContext::patchTruncated
        ));
    }

    @Test
    void limitsJavaStructureAndReportsOmittedElements() {
        CompactContextService service = new CompactContextService(
                new ContextProperties(1, 100, 100, 1, 1, 1, 1)
        );
        JavaTypeAnalysis firstType = new JavaTypeAnalysis(
                "First",
                JavaTypeKind.CLASS,
                List.of("public"),
                List.of(),
                List.of(method("first"), method("second")),
                0
        );
        JavaTypeAnalysis secondType = new JavaTypeAnalysis(
                "Second",
                JavaTypeKind.CLASS,
                List.of(),
                List.of(),
                List.of(),
                0
        );
        JavaFileAnalysis analysis = new JavaFileAnalysis(
                JAVA_PATH,
                "example",
                List.of("first.Import", "second.Import"),
                List.of(firstType, secondType),
                20,
                false,
                List.of("first problem", "second problem")
        );
        ChangedFile javaFile = changedFile(
                JAVA_PATH,
                FileChangeType.MODIFY,
                true,
                1,
                0,
                "class First {}"
        );

        CompactJavaFileContext result = service.build(
                        commit(COMMIT_HASH),
                        diff(List.of(javaFile)),
                        List.of(new ChangedJavaFileAnalysis(0, analysis))
                )
                .files()
                .getFirst()
                .javaAnalysis();

        assertEquals(List.of("first.Import"), result.imports());
        assertEquals(1, result.omittedImportCount());
        assertEquals(List.of("first problem"), result.parsingProblems());
        assertEquals(1, result.omittedParsingProblemCount());
        assertEquals(1, result.types().size());
        assertEquals(1, result.omittedTypeCount());
        assertEquals(1, result.types().getFirst().methods().size());
        assertEquals(1, result.types().getFirst().omittedMethodCount());
    }

    @Test
    void rejectsDiffFromAnotherCommit() {
        CompactContextService service = service(50, 4_000, 50);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.build(
                        commit("b".repeat(40)),
                        diff(List.of()),
                        List.of()
                )
        );
    }

    @Test
    void rejectsDuplicateJavaAnalysisPosition() {
        CompactContextService service = service(50, 4_000, 50);
        ChangedFile javaFile = changedFile(
                JAVA_PATH,
                FileChangeType.ADD,
                true,
                1,
                0,
                "class Sample {}"
        );
        JavaFileAnalysis analysis = javaAnalysis(List.of());
        ChangedJavaFileAnalysis positionedAnalysis =
                new ChangedJavaFileAnalysis(0, analysis);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.build(
                        commit(COMMIT_HASH),
                        diff(List.of(javaFile)),
                        List.of(positionedAnalysis, positionedAnalysis)
                )
        );
    }

    @Test
    void rejectsMissingAnalysisForCurrentJavaFile() {
        CompactContextService service = service(50, 4_000, 50);
        ChangedFile javaFile = changedFile(
                JAVA_PATH,
                FileChangeType.MODIFY,
                true,
                1,
                0,
                "class Sample {}"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.build(
                        commit(COMMIT_HASH),
                        diff(List.of(javaFile)),
                        List.of()
                )
        );
    }

    private CompactContextService service(
            int maxFiles,
            int maxPatchCharacters,
            int maxMethods
    ) {
        return new CompactContextService(new ContextProperties(
                maxFiles,
                maxPatchCharacters,
                maxFiles * maxPatchCharacters,
                50,
                10,
                10,
                maxMethods
        ));
    }

    private CommitProcessingData commit(String commitHash) {
        return new CommitProcessingData(
                1,
                2,
                commitHash,
                "https://example.com/project.git",
                "main",
                "Test User",
                Instant.parse("2026-07-20T10:00:00Z"),
                "Test commit"
        );
    }

    private CommitDiff diff(List<ChangedFile> files) {
        return new CommitDiff(
                COMMIT_HASH,
                "c".repeat(40),
                files,
                files.stream().mapToInt(ChangedFile::addedLines).sum(),
                files.stream().mapToInt(ChangedFile::deletedLines).sum(),
                "raw diff"
        );
    }

    private ChangedFile changedFile(
            String path,
            FileChangeType changeType,
            boolean javaFile,
            int addedLines,
            int deletedLines,
            String patch
    ) {
        String oldPath = changeType == FileChangeType.ADD ? null : path;
        String newPath = changeType == FileChangeType.DELETE ? null : path;
        return new ChangedFile(
                oldPath,
                newPath,
                changeType,
                addedLines,
                deletedLines,
                javaFile,
                patch
        );
    }

    private JavaFileAnalysis javaAnalysis(
            List<JavaMethodAnalysis> methods
    ) {
        JavaTypeAnalysis type = new JavaTypeAnalysis(
                "Sample",
                JavaTypeKind.CLASS,
                List.of("public"),
                List.of("Service"),
                methods,
                1
        );
        return new JavaFileAnalysis(
                JAVA_PATH,
                "example",
                List.of("java.util.List"),
                List.of(type),
                20,
                true,
                List.of()
        );
    }

    private JavaMethodAnalysis method(String name) {
        return new JavaMethodAnalysis(
                name,
                "void",
                List.of("String"),
                List.of("public"),
                List.of()
        );
    }
}
