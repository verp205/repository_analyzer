import type { ReportAction, ReportEvidence, AnalysisReport, AnalysisTask, CommitInsight, ReportPeriodInfo, RepositoryFacts } from "@/lib/types";

export const task: AnalysisTask = { id: 1, repositoryUrl: "https://github.com/example/project.git", branch: "main", status: "COMPLETED",
  revision: 1, headCommitHash: "abcdef1234567890abcdef1234567890abcdef1234", addedCommits: 1, createdAt: "2026-07-20", updatedAt: "2026-07-20" };
export const period: ReportPeriodInfo = { type: "ALL", label: "вся история" };
export function report(id = 10, status: AnalysisReport["status"] = "COMPLETED"): AnalysisReport {
  return { id, analysisTaskId: task.id, repositoryUrl: task.repositoryUrl, branch: task.branch, period, audience: "DEVELOPER", status,
    analysisRevision: 1, headCommitHash: task.headCommitHash, artifacts: null, totalCommits: 2, processedCommits: status === "COMPLETED" ? 2 : 0,
    content: status === "COMPLETED" ? "## Главное\n- Готово" : null, lastError: null, createdAt: "2026-07-20", updatedAt: "2026-07-20" };
}
export const facts: RepositoryFacts = { analysisTaskId: 1, repositoryUrl: task.repositoryUrl, repositoryName: "project", branch: "main",
  firstCommitAt: "2026-07-20", lastCommitAt: "2026-07-20", periodDays: 1, activeDays: 1, totalCommits: 2, analyzedCommits: 2, failedCommits: 0,
  mergeCommits: 0, addedLines: 2, deletedLines: 1, touchedFiles: 1, authorCount: 1, authors: [], activityGranularity: "DAY", activity: [], hotspots: [],
  fileTypes: [], largestCommits: [], risks: { high: 0, medium: 0, low: 0, none: 2, unknown: 0 }, categories: [], insightCommits: 2 };
export function insight(id: number): CommitInsight {
  return { commitId: id, position: id, commitHash: `${id}`.padStart(40, "a"), message: "commit", authorName: "developer", committedAt: "2026-07-20",
    addedLines: 1, deletedLines: 0, changedFiles: 1, status: "ANALYZED", category: "FIX", categoryName: "Исправление", riskLevel: "LOW", summary: "Изменение",
    details: { keyChanges: [], behaviorImpact: "", risks: [], securityNotes: [], testNotes: "", checks: [], pullRequest: null } };
}

export const sources: ReportEvidence[] = [
  { id: "C101", kind: "COMMIT", commitId: 101, commitHash: "abcdef1234567890abcdef1234567890abcdef1234", label: "Payment validation", paths: ["src/Payment.java"], description: "Stored diff" },
  { id: "C102", kind: "COMMIT", commitId: 102, commitHash: "bbbbbbb1234567890abcdef1234567890abcdef1234", label: "Timeout", paths: ["src/Client.java"], description: "Stored diff" },
];
export const actions: ReportAction[] = [
  { id: "A1", title: "Проверить отрицательную сумму", reason: "Удалена проверка суммы", verification: "Сумма -1 отклоняется, баланс неизменен", priority: "HIGH", evidenceIds: ["C101"], paths: ["src/Payment.java"] },
  { id: "A2", title: "Проверить таймаут", reason: "Изменён HTTP-клиент", verification: "Задержка сервера завершает запрос ошибкой таймаута", priority: "MEDIUM", evidenceIds: ["C102"], paths: ["src/Client.java"] },
];

