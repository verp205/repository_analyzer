package com.ivan_naumenko.repository_analyzer.javaanalysis.mapper;

import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileEntity;
import com.ivan_naumenko.repository_analyzer.javaanalysis.entity.JavaFileAnalysisEntity;
import com.ivan_naumenko.repository_analyzer.javaanalysis.entity.JavaMethodAnalysisEntity;
import com.ivan_naumenko.repository_analyzer.javaanalysis.entity.JavaTypeAnalysisEntity;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaMethodAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaTypeAnalysis;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class JavaAnalysisMapper {

    private static final String LIST_SEPARATOR = "\n";

    public JavaFileAnalysisEntity toEntity(
            ChangedFileEntity changedFile,
            JavaFileAnalysis analysis
    ) {
        Objects.requireNonNull(changedFile, "changedFile не должен быть null");
        Objects.requireNonNull(analysis, "analysis не должен быть null");

        if (!Objects.equals(changedFile.getNewPath(), analysis.filePath())) {
            throw new IllegalArgumentException(
                    "Путь Java-анализа не совпадает с путём изменённого файла"
            );
        }

        JavaFileAnalysisEntity entity = new JavaFileAnalysisEntity();
        entity.setChangedFile(changedFile);
        entity.setPackageName(analysis.packageName());
        entity.setImportsText(join(analysis.imports()));
        entity.setLineCount(analysis.lineCount());
        entity.setParsingSuccessful(analysis.parsingSuccessful());
        entity.setProblemsText(join(analysis.problems()));

        for (int position = 0; position < analysis.types().size(); position++) {
            entity.addType(toTypeEntity(
                    analysis.types().get(position),
                    position
            ));
        }

        return entity;
    }

    private JavaTypeAnalysisEntity toTypeEntity(
            JavaTypeAnalysis analysis,
            int position
    ) {
        JavaTypeAnalysisEntity entity = new JavaTypeAnalysisEntity();
        entity.setTypePosition(position);
        entity.setName(analysis.name());
        entity.setKind(analysis.kind());
        entity.setModifiersText(join(analysis.modifiers()));
        entity.setAnnotationsText(join(analysis.annotations()));
        entity.setConstructorCount(analysis.constructorCount());

        for (int methodPosition = 0;
             methodPosition < analysis.methods().size();
             methodPosition++) {
            entity.addMethod(toMethodEntity(
                    analysis.methods().get(methodPosition),
                    methodPosition
            ));
        }

        return entity;
    }

    private JavaMethodAnalysisEntity toMethodEntity(
            JavaMethodAnalysis analysis,
            int position
    ) {
        JavaMethodAnalysisEntity entity = new JavaMethodAnalysisEntity();
        entity.setMethodPosition(position);
        entity.setName(analysis.name());
        entity.setReturnType(analysis.returnType());
        entity.setParameterTypesText(join(analysis.parameterTypes()));
        entity.setModifiersText(join(analysis.modifiers()));
        entity.setAnnotationsText(join(analysis.annotations()));
        return entity;
    }

    private String join(List<String> values) {
        return String.join(LIST_SEPARATOR, values);
    }
}
