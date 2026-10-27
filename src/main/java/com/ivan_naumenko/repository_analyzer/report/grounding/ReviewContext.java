package com.ivan_naumenko.repository_analyzer.report.grounding;

import com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.Citation;
import java.util.List;

/** Immutable source material copied from the stored diff and the exact Git revisions. */
public record ReviewContext(int commitId, String hash, List<SourceFile> files, List<Citation> citations,
                            int inspectedFiles, int limitedFiles, int contextFiles, List<String> limitations) {
    public record SourceFile(String oldPath, String path, String changeType, int added, int deleted, String patch) {}
    public ReviewContext {
        files = List.copyOf(files); citations = List.copyOf(citations); limitations = List.copyOf(limitations);
    }
}
