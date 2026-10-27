package com.ivan_naumenko.repository_analyzer.llm.context.service;

import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactCommitContext;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactFileContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@RequiredArgsConstructor
@Service
public class CompactContextSerializer {

    private final ObjectMapper mapper;

    public String serialize(CompactCommitContext context) {
        Objects.requireNonNull(
                context,
                "context не должен быть null"
        );

        return mapper.writeValueAsString(context);
    }

    /** Language inventory is report metadata; preserve the reviewed model input for every language. */
    public String serializeForAnalysis(CompactCommitContext context) {
        Objects.requireNonNull(context, "context не должен быть null");
        if (context.files().stream().noneMatch(file -> file.language() != null)) return serialize(context);
        var files = context.files().stream().map(file -> new CompactFileContext(file.path(), file.changeType(),
                file.addedLines(), file.deletedLines(), file.patch(), file.patchTruncated(), file.javaAnalysis())).toList();
        return serialize(new CompactCommitContext(context.commitHash(), context.parentHash(), context.message(),
                context.authorName(), context.committedAt(), context.totalAddedLines(), context.totalDeletedLines(),
                files, context.omittedFileCount()));
    }
}
