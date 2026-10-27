import type { ReportArtifacts, ReportCitation } from "@/lib/types";

export function reportCitations(artifacts: ReportArtifacts): ReportCitation[] {
  const unique = new Map<string, ReportCitation>();
  for (const finding of [...artifacts.summary, ...artifacts.sections.flatMap(section => section.findings)]) {
    for (const citation of finding.assessment?.citations ?? []) {
      unique.set(`${citation.commitHash}:${citation.path}:${citation.side}:${citation.startLine}:${citation.endLine}`, citation);
    }
  }
  return [...unique.values()];
}

export function citationMarkdown(artifacts: ReportArtifacts): string {
  const lines: string[] = [];
  for (const citation of reportCitations(artifacts)) {
    // A source may itself contain Markdown fences: make the enclosing fence longer.
    const fence = "`".repeat(Math.max(3, ...[...citation.code.matchAll(/`+/g)].map(match => match[0].length + 1)));
    lines.push(`### ${citation.path} · ${citation.side} · ${citation.startLine}–${citation.endLine}`, "",
      `Версия: ${citation.commitHash}. Источник: [${citation.evidenceId}](#evidence-${citation.evidenceId}).`, "",
      fence, citation.code, fence, "");
  }
  return lines.length ? ["## Фрагменты кода", "", ...lines].join("\n") : "";
}
