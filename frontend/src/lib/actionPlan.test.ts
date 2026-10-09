import { describe, expect, it } from "vitest";
import { actionPlanJson, buildActionPlanMarkdown } from "@/lib/actionPlan";
import { linkCommitReferences, parseReportSections } from "@/lib/reportContent";
import { buildReportMarkdown } from "@/lib/markdownExport";
import { actions, sources, insight, report } from "@/test/fixtures";
import type { ReportArtifacts } from "@/lib/types";


describe("reviewable exports", () => {
  it("exports selected tasks with reason, source, location and observable verification", () => {
    const markdown = buildActionPlanMarkdown([actions[0]], sources, report().repositoryUrl);
    expect(markdown).toContain(actions[0].verification);
    expect(markdown).toContain(actions[0].reason);
    expect(markdown).toContain("src/Payment.java");
    expect(markdown).toContain(`/commit/${sources[0].commitHash}`);
    expect(markdown).not.toContain(actions[1].title);
    const json = JSON.parse(actionPlanJson([actions[0]], sources, 10));
    expect(json.reportId).toBe(10);
    expect(json.actions).toHaveLength(1);
    expect(json.evidence).toEqual([sources[0]]);
  });
  it("keeps source anchors and action criteria in a complete Markdown report", () => {
    const artifacts: ReportArtifacts = { summary: [], sections: [], actions, evidence: sources,
      quality: { status: "CHECKED", validatedFindings: 1, validatedActions: 2, checks: ["Проверены ссылки"], warnings: ["Интерпретация требует проверки"] } };
    const parsed = parseReportSections("## Главное\n- Проверка суммы удалена [C101](#evidence-C101)\n\n## Риски\n- Проверить баланс [C101](#evidence-C101)");
    const markdown = buildReportMarkdown({ title: "Отчёт", subtitle: "Версия 1", audience: "DEVELOPER", ...parsed,
      facts: null, commits: [], repositoryUrl: report().repositoryUrl, artifacts });
    expect(markdown).toContain('id="evidence-C101"');
    expect(markdown).toContain("[C101](#evidence-C101)");
    expect(markdown).toContain(actions[0].verification);
    expect(markdown).toContain("Проверены ссылки");
  });
  it("does not corrupt existing Markdown links or fenced code when linking hashes", () => {
    const commit = { ...insight(1), commitHash: sources[0].commitHash! };
    const text = "[existing](https://github.com/example/project/commit/abcdef1234567890abcdef1234567890abcdef1234)\n```\nabcdef1\n```\n(abcdef1)";
    const linked = linkCommitReferences(text, [commit], report().repositoryUrl);
    expect(linked).toContain("[existing](https://github.com/example/project/commit/abcdef1234567890abcdef1234567890abcdef1234)");
    expect(linked).toContain("```\nabcdef1\n```");
    expect(linked).toContain("([`abcdef1`](https://github.com/example/project/commit/");
  });
  it("does not link ambiguous hash prefixes", () => {
    const first = { ...insight(1), commitHash: "abcdef1" + "a".repeat(33) };
    const second = { ...insight(2), commitHash: "abcdef1" + "b".repeat(33) };
    expect(linkCommitReferences("abcdef1", [first, second], report().repositoryUrl)).toBe("abcdef1");
  });
});
