import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { GroundedFindings, ReviewCoverage } from "./GroundedFindings";
import { citationMarkdown, reportCitations } from "@/lib/reportGrounding";
import { buildReportMarkdown } from "@/lib/markdownExport";
import type { ReportArtifacts, ReportFinding } from "@/lib/types";

const finding: ReportFinding = { text: "Удалён вызов checkSession", evidenceIds: ["C101"], assessment: {
  kind: "INFERENCE", observation: "Удалён вызов checkSession", impact: "Если другой защиты нет, запрос достигнет обработчика.",
  riskLevel: "HIGH", risk: "Возможен обход проверки сессии.", unknowns: ["Внешнее middleware не проверено."],
  citations: [{ id: "D0", evidenceId: "C101", path: "src/auth.ts", commitHash: "abcdef1234567890", side: "DIFF",
    startLine: 1, endLine: 4, code: "-checkSession();\n\n\n+next(); // ```" }],
} };
const artifacts: ReportArtifacts = { summary: [finding], sections: [{ title: "Риски", findings: [finding] }], actions: [], evidence: [],
  quality: { status: "PARTIAL", validatedFindings: 1, validatedActions: 0, checks: [], warnings: [] },
  coverage: { totalCommits: 2, reviewedCommits: 1, unreviewedCommits: 1, changedFiles: 3, inspectedFiles: 2, limitedFiles: 1,
    contextFiles: 1, rejectedResponses: 1, limitations: ["Diff обрезан."], reviewedRisks: { "101": "HIGH", "102": "UNKNOWN" } },
};

describe("grounded report", () => {
  it("separates interpretations, unknowns and literal source code and opens the right source", () => {
    const select = vi.fn();
    render(<GroundedFindings findings={[finding]} onEvidence={select} />);
    expect(screen.getByText("Оценка модели")).toBeInTheDocument();
    expect(screen.getByText(/Внешнее middleware не проверено/)).toBeInTheDocument();
    fireEvent.click(screen.getByText(/Код и источники/));
    expect(screen.getByLabelText("Цитата src/auth.ts")).toHaveTextContent("-checkSession();");
    fireEvent.click(screen.getByRole("button", { name: /src\/auth.ts/ }));
    expect(select).toHaveBeenCalledWith("C101");
  });
  it("makes partial coverage visible without an accuracy percentage", () => {
    render(<ReviewCoverage artifacts={artifacts} />);
    expect(screen.getByText("Проверка с ограничениями")).toBeInTheDocument();
    expect(screen.getByText(/Коммиты: 1 из 2/)).toBeInTheDocument();
    expect(screen.getByText(/Тесты не запускались/)).toBeInTheDocument();
  });
  it("exports exact quotes once, with safe Markdown fences and pinned revision", () => {
    expect(reportCitations(artifacts)).toHaveLength(1);
    const md = citationMarkdown(artifacts);
    expect(md).toContain("````\n-checkSession();\n\n\n+next(); // ```\n````");
    expect(md).toContain("abcdef1234567890");
    const full = buildReportMarkdown({ title: "Отчёт", subtitle: "", audience: "DEVELOPER", summary: [], sections: [],
      facts: null, commits: [], repositoryUrl: "", artifacts });
    expect(full).toContain(md);
  });
  it("does not present attached sources as independent semantic verification", () => {
    render(<ReviewCoverage artifacts={{ ...artifacts, coverage: { ...artifacts.coverage!, mode: "SOURCES_ONLY",
      reviewedCommits: 0, unreviewedCommits: 2, reviewedRisks: {} } }} />);
    expect(screen.getByText("Факты и источники")).toBeInTheDocument();
    expect(screen.getByText(/Независимая смысловая проверка не выполнялась/)).toBeInTheDocument();
    expect(screen.queryByText(/Коммиты: 0 из 2/)).not.toBeInTheDocument();
    expect(screen.getByText(/Фрагменты diff файлов: 2 из 3/)).toBeInTheDocument();
  });
});
