import type {
  AnalysisTaskStatus,
  ReportAudience,
  ReportStatus,
  RiskLevel,
} from "@/lib/types";

const taskStatusLabels: Record<AnalysisTaskStatus, string> = {
  NEW: "Ожидает запуска",
  CLONING: "Клонирование",
  DISCOVERING_COMMITS: "Поиск коммитов",
  PROCESSING: "Анализируется",
  COMPLETED: "Готово",
  FAILED: "Ошибка",
  CANCELLED: "Остановлен",
};

const reportStatusLabels: Record<ReportStatus, string> = {
  NEW: "Ожидает запуска",
  PROCESSING: "Формируется",
  COMPLETED: "Готово",
  FAILED: "Ошибка",
  CANCELLED: "Остановлен",
};

const audienceLabels: Record<ReportAudience, string> = {
  DEVELOPER: "Разработчик",
  ANALYST: "Аналитик",
  SECURITY: "Специалист по безопасности",
  TEAM_LEAD: "Тимлид",
  DIRECTOR: "Директор",
};

const audienceReaderLabels: Record<ReportAudience, string> = {
  DEVELOPER: "разработчика",
  ANALYST: "аналитика",
  SECURITY: "специалиста по безопасности",
  TEAM_LEAD: "тимлида",
  DIRECTOR: "директора",
};

const riskLabels: Record<RiskLevel, string> = {
  HIGH: "Высокий",
  MEDIUM: "Средний",
  LOW: "Низкий",
  NONE: "Нет риска",
  UNKNOWN: "Не определён",
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

export function riskLabel(risk: RiskLevel) {
  return riskLabels[risk];
}

export function reportTitle(
  projectName: string,
  branch: string,
  audience: ReportAudience,
) {
  return `Отчёт по проекту «${projectName}», ветка «${branch}», для ${audienceReaderLabels[audience]}`;
}
