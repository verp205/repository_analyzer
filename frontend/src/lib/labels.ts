import type {
  AnalysisTaskStatus,
  ReportAudience,
  ReportStatus,
} from "@/lib/types";

const taskStatusLabels: Record<AnalysisTaskStatus, string> = {
  NEW: "Ожидает запуска",
  CLONING: "Клонирование",
  DISCOVERING_COMMITS: "Поиск коммитов",
  PROCESSING: "Анализируется",
  COMPLETED: "Готово",
  FAILED: "Ошибка",
};

const reportStatusLabels: Record<ReportStatus, string> = {
  NEW: "Ожидает запуска",
  PROCESSING: "Формируется",
  COMPLETED: "Готово",
  FAILED: "Ошибка",
};

const audienceLabels: Record<ReportAudience, string> = {
  DEVELOPER: "Разработчик",
  ANALYST: "Аналитик",
  SECURITY: "Специалист по безопасности",
  TEAM_LEAD: "Тимлид",
  DIRECTOR: "Директор",
};

export function taskStatusLabel(status: AnalysisTaskStatus) {
  return taskStatusLabels[status];
}

export function reportStatusLabel(status: ReportStatus) {
  return reportStatusLabels[status];
}

export function audienceLabel(audience: ReportAudience) {
  return audienceLabels[audience];
}
