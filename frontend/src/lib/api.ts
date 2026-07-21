import type {
  AnalysisProgress,
  AnalysisReport,
  AnalysisTask,
  CommitClusters,
  CommitGraph,
  ReportAudience,
} from "@/lib/types";

export const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080"
).replace(/\/$/, "");

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
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

export const api = {
  listTasks: () => request<AnalysisTask[]>("/api/analysis-tasks"),
  getTask: (taskId: number) =>
    request<AnalysisTask>(`/api/analysis-tasks/${taskId}`),
  getProgress: (taskId: number) =>
    request<AnalysisProgress>(`/api/analysis-tasks/${taskId}/progress`),
  createTask: (repositoryUrl: string, branch: string) =>
    request<AnalysisTask>("/api/analysis-tasks", {
      method: "POST",
      body: JSON.stringify({ repositoryUrl, branch }),
    }),
  retryTask: (taskId: number) =>
    request<AnalysisTask>(`/api/analysis-tasks/${taskId}/retry`, {
      method: "POST",
    }),
  getGraph: (taskId: number) =>
    request<CommitGraph>(`/api/analysis-tasks/${taskId}/graph`),
  getClusters: (taskId: number) =>
    request<CommitClusters>(`/api/analysis-tasks/${taskId}/clusters`),
  listReports: (taskId: number) =>
    request<AnalysisReport[]>(`/api/analysis-tasks/${taskId}/reports`),
  createReport: (taskId: number, audience: ReportAudience) =>
    request<AnalysisReport>(`/api/analysis-tasks/${taskId}/reports`, {
      method: "POST",
      body: JSON.stringify({ audience }),
    }),
  getReport: (reportId: number) =>
    request<AnalysisReport>(`/api/reports/${reportId}`),
  retryReport: (reportId: number) =>
    request<AnalysisReport>(`/api/reports/${reportId}/retry`, {
      method: "POST",
    }),
  reportEventsUrl: (reportId: number) =>
    `${API_BASE_URL}/api/reports/${reportId}/events`,
};
