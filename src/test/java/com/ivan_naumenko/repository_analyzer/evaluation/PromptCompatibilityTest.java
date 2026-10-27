package com.ivan_naumenko.repository_analyzer.evaluation;

import com.ivan_naumenko.repository_analyzer.code.TextCodeLanguages;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightPromptFactory;
import com.ivan_naumenko.repository_analyzer.llm.context.model.*;
import com.ivan_naumenko.repository_analyzer.llm.context.service.CompactContextSerializer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PromptCompatibilityTest {
    private final CompactContextSerializer serializer = new CompactContextSerializer(JsonMapper.builder().build());
    private final CommitInsightPromptFactory current = new CommitInsightPromptFactory(serializer);
    private final LegacyCommitInsightPromptFactory legacy = new LegacyCommitInsightPromptFactory(serializer);

    @Test void preservesOriginalSingleAndBatchPromptsAndOmittedLanguageField() {
        var contexts = GoldenDataset.load().stream().map(GoldenCommitCase::toContext).toList();
        for (var context : contexts) {
            assertEquals(legacy.buildSingle(context), current.buildSingle(context));
            assertFalse(serializer.serialize(context).contains("\"language\""));
        }
        for (int start = 0; start < contexts.size(); start += 5) {
            var group = contexts.subList(start, Math.min(start + 5, contexts.size()));
            assertEquals(legacy.buildBatch(group), current.buildBatch(group));
        }
    }

    @Test void givesEveryTextLanguageItsDiffWithoutInventingAnAst() {
        for (var golden : GoldenDataset.load("/evaluation/multilanguage-golden.json")) {
            var context = withLanguages(golden.toContext());
            String prompt = current.buildSingle(context);
            assertEquals(legacy.buildSingle(golden.toContext()), prompt);
            assertTrue(prompt.contains(golden.commit().files().getFirst().path()));
            assertEquals(golden.commit().files().getFirst().patch(), context.files().getFirst().patch());
            assertNotNull(context.files().getFirst().language());
            assertNull(context.files().getFirst().javaAnalysis());
        }
        var cases = GoldenDataset.load("/evaluation/multilanguage-golden.json");
        assertEquals(legacy.buildBatch(cases.stream().map(GoldenCommitCase::toContext).toList()),
                current.buildBatch(cases.stream().map(golden -> withLanguages(golden.toContext())).toList()));
    }

    static CompactCommitContext withLanguages(CompactCommitContext context) {
        List<CompactFileContext> files = context.files().stream().map(file -> new CompactFileContext(file.path(),
                file.changeType(), file.addedLines(), file.deletedLines(), file.patch(), file.patchTruncated(),
                file.javaAnalysis(), TextCodeLanguages.extraContextLanguage(file.path(), file.patch()))).toList();
        return new CompactCommitContext(context.commitHash(), context.parentHash(), context.message(), context.authorName(),
                context.committedAt(), context.totalAddedLines(), context.totalDeletedLines(), files, context.omittedFileCount());
    }
}
