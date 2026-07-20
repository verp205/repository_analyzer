package com.ivan_naumenko.repository_analyzer.llm.context.service;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingData;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFile;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiff;
import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.ChangedJavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaMethodAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaTypeAnalysis;
import com.ivan_naumenko.repository_analyzer.llm.context.config.ContextProperties;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactCommitContext;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactFileContext;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactJavaFileContext;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactJavaMethodContext;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactJavaTypeContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CompactContextService {

    private final ContextProperties properties;

    public CompactCommitContext build(
            CommitProcessingData commit,
            CommitDiff diff,
            List<ChangedJavaFileAnalysis> javaAnalyses
    ) {
        Objects.requireNonNull(commit, "commit не должен быть null");
        Objects.requireNonNull(diff, "diff не должен быть null");
        Objects.requireNonNull(
                javaAnalyses,
                "javaAnalyses не должен быть null"
        );

        if (!Objects.equals(commit.commitHash(), diff.commitHash())) {
            throw new IllegalArgumentException(
                    "Хеш diff не совпадает с хешем коммита"
            );
        }

        Map<Integer, JavaFileAnalysis> analysesByPosition =
                indexJavaAnalyses(diff, javaAnalyses);

        int fileLimit = Math.min(
                diff.changedFiles().size(),
                properties.maxFiles()
        );

        List<CompactFileContext> files = new ArrayList<>(fileLimit);

        for (int position = 0; position < fileLimit; position++) {
            ChangedFile changedFile = diff.changedFiles().get(position);
            JavaFileAnalysis javaAnalysis = analysesByPosition.get(position);

            if (requiresJavaAnalysis(changedFile) && javaAnalysis == null) {
                throw new IllegalArgumentException(
                        "Отсутствует Java-анализ файла с позицией "
                                + position
                );
            }

            files.add(toFileContext(changedFile, javaAnalysis));
        }

        int omittedFileCount = diff.changedFiles().size() - fileLimit;

        return new CompactCommitContext(
                commit.commitHash(),
                diff.parentHash(),
                commit.message(),
                commit.authorName(),
                commit.committedAt(),
                diff.totalAddedLines(),
                diff.totalDeletedLines(),
                files,
                omittedFileCount
        );
    }

    private Map<Integer, JavaFileAnalysis> indexJavaAnalyses(
            CommitDiff diff,
            List<ChangedJavaFileAnalysis> javaAnalyses
    ) {
        Map<Integer, JavaFileAnalysis> result = new HashMap<>();

        for (ChangedJavaFileAnalysis item : javaAnalyses) {
            Objects.requireNonNull(
                    item,
                    "Элемент javaAnalyses не должен быть null"
            );
            int position = item.filePosition();

            if (position >= diff.changedFiles().size()) {
                throw new IllegalArgumentException(
                        "Позиция Java-анализа выходит за границы diff: "
                                + position
                );
            }

            ChangedFile changedFile = diff.changedFiles().get(position);
            if (!requiresJavaAnalysis(changedFile)) {
                throw new IllegalArgumentException(
                        "Файл с позицией "
                                + position
                                + " не требует Java-анализа"
                );
            }
            if (!Objects.equals(
                    changedFile.newPath(),
                    item.analysis().filePath()
            )) {
                throw new IllegalArgumentException(
                        "Путь Java-анализа не совпадает с файлом diff "
                                + "на позиции "
                                + position
                );
            }

            JavaFileAnalysis previous = result.putIfAbsent(
                    position,
                    item.analysis()
            );
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Дублирующийся Java-анализ позиции " + position
                );
            }
        }

        return result;
    }

    private CompactFileContext toFileContext(
            ChangedFile changedFile,
            JavaFileAnalysis javaAnalysis
    ) {
        TruncatedText patch = truncatePatch(changedFile.patch());
        CompactJavaFileContext compactJavaAnalysis = javaAnalysis == null
                ? null
                : toJavaFileContext(javaAnalysis);

        return new CompactFileContext(
                changedFile.path(),
                changedFile.changeType(),
                changedFile.addedLines(),
                changedFile.deletedLines(),
                patch.value(),
                patch.truncated(),
                compactJavaAnalysis
        );
    }

    private CompactJavaFileContext toJavaFileContext(
            JavaFileAnalysis analysis
    ) {
        List<CompactJavaTypeContext> types = analysis.types()
                .stream()
                .map(this::toTypeContext)
                .toList();

        return new CompactJavaFileContext(
                analysis.packageName(),
                analysis.imports(),
                analysis.lineCount(),
                analysis.parsingSuccessful(),
                analysis.problems(),
                types
        );
    }

    private CompactJavaTypeContext toTypeContext(JavaTypeAnalysis analysis) {
        int methodLimit = Math.min(
                analysis.methods().size(),
                properties.maxMethodsPerType()
        );
        List<CompactJavaMethodContext> methods = new ArrayList<>(methodLimit);

        for (int position = 0; position < methodLimit; position++) {
            methods.add(toMethodContext(analysis.methods().get(position)));
        }

        int omittedMethodCount = analysis.methods().size() - methodLimit;

        return new CompactJavaTypeContext(
                analysis.name(),
                analysis.kind(),
                analysis.modifiers(),
                analysis.annotations(),
                analysis.constructorCount(),
                methods,
                omittedMethodCount
        );
    }

    private CompactJavaMethodContext toMethodContext(
            JavaMethodAnalysis analysis
    ) {
        return new CompactJavaMethodContext(
                analysis.name(),
                analysis.returnType(),
                analysis.parameterTypes(),
                analysis.modifiers(),
                analysis.annotations()
        );
    }

    private TruncatedText truncatePatch(String patch) {
        int limit = properties.maxPatchCharactersPerFile();

        if (patch.length() <= limit) {
            return new TruncatedText(patch, false);
        }

        return new TruncatedText(patch.substring(0, limit), true);
    }

    private boolean requiresJavaAnalysis(ChangedFile changedFile) {
        return changedFile.javaFile()
                && changedFile.changeType() != FileChangeType.DELETE
                && changedFile.newPath() != null;
    }

    private record TruncatedText(
            String value,
            boolean truncated
    ) {
    }
}
