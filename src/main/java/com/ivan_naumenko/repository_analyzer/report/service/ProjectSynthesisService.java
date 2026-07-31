package com.ivan_naumenko.repository_analyzer.report.service;

import com.ivan_naumenko.repository_analyzer.insight.cluster.dto.CommitClustersResponse;
import com.ivan_naumenko.repository_analyzer.insight.graph.dto.CommitGraphResponse;
import com.ivan_naumenko.repository_analyzer.llm.coordinator.LlmRequestCoordinator;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import com.ivan_naumenko.repository_analyzer.llm.prompt.service.ProjectSynthesisPromptFactory;
import com.ivan_naumenko.repository_analyzer.report.config.ReportProperties;
import com.ivan_naumenko.repository_analyzer.report.model.CommitReportResult;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectSynthesisService {

    private final ProjectSynthesisPromptFactory promptFactory;
    private final LlmRequestCoordinator requestCoordinator;
    private final ReportTextSanitizer sanitizer;
    private final ReportProperties properties;

    public String synthesize(
            ReportGenerationData report,
            CommitGraphResponse graph,
            CommitClustersResponse clusters,
            List<CommitReportResult> commitResults
    ) {
        List<String> sources = commitResults.stream()
                .map(this::toSource)
                .toList();

        int pass = 1;
        while (totalCharacters(sources) > properties.synthesisBatchChars()
                && pass <= properties.synthesisMaxPasses()) {
            List<List<String>> batches = splitIntoBatches(
                    sources,
                    properties.synthesisBatchChars()
            );
            List<String> summaries = new ArrayList<>(batches.size());
            for (int index = 0; index < batches.size(); index++) {
                String prompt = promptFactory.buildIntermediate(
                        report,
                        pass,
                        index,
                        batches.size(),
                        batches.get(index)
                );
                summaries.add(sanitizer.sanitize(
                        requestCoordinator.getOrGenerate(
                                prompt,
                                report.audience(),
                                LlmRequestProfile.SYNTHESIS_BATCH
                        )
                ));
            }
            sources = summaries;
            pass++;
        }

        String finalPrompt = promptFactory.buildFinal(
                report,
                graph,
                clusters,
                sources
        );
        return sanitizer.sanitize(requestCoordinator.getOrGenerate(
                finalPrompt,
                report.audience(),
                LlmRequestProfile.SYNTHESIS_FINAL
        ));
    }

    private String toSource(CommitReportResult result) {
        return """
                COMMIT_POSITION: %d
                %s
                """.formatted(
                result.position(),
                result.content()
        );
    }

    private List<List<String>> splitIntoBatches(
            List<String> sources,
            int maxCharacters
    ) {
        List<List<String>> batches = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentCharacters = 0;

        for (String source : sources) {
            if (!current.isEmpty()
                    && currentCharacters + source.length() > maxCharacters) {
                batches.add(List.copyOf(current));
                current.clear();
                currentCharacters = 0;
            }
            current.add(source);
            currentCharacters += source.length();
        }

        if (!current.isEmpty()) {
            batches.add(List.copyOf(current));
        }
        return batches;
    }

    private int totalCharacters(List<String> sources) {
        return sources.stream().mapToInt(String::length).sum();
    }
}
