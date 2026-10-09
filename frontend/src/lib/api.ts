import type {
  AnalysisProgress,
  AnalysisReport,
  ReportSummary,
  AnalysisTask,
  EvidenceDetail,
  EvidenceFile,
  CommitClusters,
  CommitGraph,
  CommitInsight,
  GitTag,
  ReportAudience,
  ReportPeriod,
  RepositoryFacts,
} from "@/lib/types";

export const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || "http://127.0.0.1:8080"
).replace(/\/$/, "");

/** Нужен, только если на backend задан API_TOKEN. */
const API_TOKEN: string = import.meta.env.VITE_API_TOKEN || "";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(API_TOKEN ? { "X-Api-Token": API_TOKEN } : {}),
      ...init?.headers,
    },
  });

  if (!response.ok) {
    let message = `Запрос завершился с кодом ${response.status}`;
    try {
      const error = (await response.json()) as { message?: string };
      if (error.message) message = error.message;
    } catch {
      // The backend may return an empty response for infrastructure errors.
    }
    throw new Error(message);
  }

  return response.json() as Promise<T>;
}

export function toPeriodRequest(period: ReportPeriod): ReportPeriod {
  return {
    type: period.type,
    days: period.days ?? null,
    fromTag: period.fromTag ?? null,
    toTag: period.toTag ?? null,
  };
}

export const api = {
  listTasks: () => request<AnalysisTask[]>("/api/analysis-tasks"),
  getTask: (taskId: number) =>
    request<AnalysisTask>(`/api/analysis-tasks/${taskId}`),
  getProgress: (taskId: number) =>
    request<AnalysisProgress>(`/api/analysis-tasks/${taskId}/progress`),
  createTask: (
    repositoryUrl: string,
    branch: string,
    gitUsername?: string,
    gitAccessToken?: string,
  ) =>
    request<AnalysisTask>("/api/analysis-tasks", {
      method: "POST",
      body: JSON.stringify({
        repositoryUrl,
        branch,
        gitUsername,
        gitAccessToken,
      }),
    }),
  retryTask: (
    taskId: number,
    gitUsername?: string,
    gitAccessToken?: string,
  ) =>
    request<AnalysisTask>(`/api/analysis-tasks/${taskId}/retry`, {
      method: "POST",
      body: JSON.stringify({ gitUsername, gitAccessToken }),
    }),
  refreshTask: (taskId: number, gitUsername?: string, gitAccessToken?: string) =>
    request<AnalysisTask>(`/api/analysis-tasks/${taskId}/refresh`, {
      method: "POST",
      body: JSON.stringify({ gitUsername, gitAccessToken }),
    }),
  getEvidence: (reportId: number, evidenceId: string) =>
    request<EvidenceDetail>(`/api/reports/${reportId}/evidence/${encodeURIComponent(evidenceId)}`),
  getEvidenceFile: (reportId: number, evidenceId: string, position: number) =>
    request<EvidenceFile>(`/api/reports/${reportId}/evidence/${encodeURIComponent(evidenceId)}/files/${position}`),
  cancelTask: (taskId: number) =>
    request<AnalysisTask>(`/api/analysis-tasks/${taskId}/cancel`, {
      method: "POST",
    }),
  getGraph: (taskId: number) =>
    request<CommitGraph>(`/api/analysis-tasks/${taskId}/graph`),
  getClusters: (taskId: number) =>
    request<CommitClusters>(`/api/analysis-tasks/${taskId}/clusters`),
  getTags: (taskId: number) =>
    request<GitTag[]>(`/api/analysis-tasks/${taskId}/tags`),
  listReports: (taskId: number) =>
    request<ReportSummary[]>(`/api/analysis-tasks/${taskId}/report-summaries`),
  createReport: (
    taskId: number,
    audience: ReportAudience,
    period: ReportPeriod,
  ) =>
    request<AnalysisReport>(`/api/analysis-tasks/${taskId}/reports`, {
      method: "POST",
      body: JSON.stringify({ audience, period: toPeriodRequest(period) }),
    }),
  getReport: (reportId: number) =>
    request<AnalysisReport>(`/api/reports/${reportId}`),
  getReportFacts: (reportId: number) =>
    request<RepositoryFacts>(`/api/reports/${reportId}/facts`),
  getReportCommitInsights: (reportId: number) =>
    request<CommitInsight[]>(`/api/reports/${reportId}/commit-insights`),
  retryReport: (reportId: number) =>
    request<AnalysisReport>(`/api/reports/${reportId}/retry`, {
      method: "POST",
    }),
  cancelReport: (reportId: number) =>
    request<AnalysisReport>(`/api/reports/${reportId}/cancel`, {
      method: "POST",
    }),
  reportEventsUrl: (reportId: number) =>
    `${API_BASE_URL}/api/reports/${reportId}/events${
      API_TOKEN ? `?access_token=${encodeURIComponent(API_TOKEN)}` : ""
    }`,
};
