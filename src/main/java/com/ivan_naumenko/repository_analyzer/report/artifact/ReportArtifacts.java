package com.ivan_naumenko.repository_analyzer.report.artifact;

import java.util.List;

/** Sources come from stored analysis data; the model only selects their IDs. */
public record ReportArtifacts(
        List<Finding> summary,
        List<Section> sections,
        List<Action> actions,
        List<Evidence> evidence,
        Quality quality,
        Coverage coverage
) {
    public ReportArtifacts(List<Finding> summary, List<Section> sections, List<Action> actions, List<Evidence> evidence, Quality quality) {
        this(summary, sections, actions, evidence, quality, null);
    }
    public record Finding(String text, List<String> evidenceIds, Assessment assessment) {
        public Finding(String text, List<String> evidenceIds) { this(text, evidenceIds, null); }
    }
    public record Citation(String id, String evidenceId, String path, String commitHash, String side,
                           int startLine, int endLine, String code) {}
    public record Assessment(String kind, String observation, String impact, String riskLevel, String risk,
                             List<String> unknowns, List<Citation> citations) {}
    public record Coverage(int totalCommits, int reviewedCommits, int unreviewedCommits,
                           int changedFiles, int inspectedFiles, int limitedFiles, int contextFiles,
                           int rejectedResponses, List<String> limitations,
                           java.util.Map<Integer, String> reviewedRisks, String mode) {
        public Coverage(int totalCommits, int reviewedCommits, int unreviewedCommits, int changedFiles,
                        int inspectedFiles, int limitedFiles, int contextFiles, int rejectedResponses,
                        List<String> limitations, java.util.Map<Integer, String> reviewedRisks) {
            this(totalCommits, reviewedCommits, unreviewedCommits, changedFiles, inspectedFiles, limitedFiles,
                    contextFiles, rejectedResponses, limitations, reviewedRisks, "REVIEWED");
        }
    }
    public record Section(String title, List<Finding> findings) {}
    public record Action(String id, String title, String reason, String verification,
                         String priority, List<String> evidenceIds, List<String> paths) {}
    public record Evidence(String id, String kind, Integer commitId, String commitHash,
                           String label, List<String> paths, String description) {}
    public record Quality(String status, int validatedFindings, int validatedActions,
                          List<String> checks, List<String> warnings) {}
    public record EvidenceFile(String oldPath, String newPath, String changeType,
                               int addedLines, int deletedLines, String patch, boolean truncated) {}
    public record EvidenceDetail(Evidence source, List<EvidenceFile> files) {}
    public record SynthesisResult(String content, ReportArtifacts artifacts) {}
}
