import type { ReportAction, ReportEvidence } from "@/lib/types";
import { commitUrl } from "@/lib/utils";

export function buildActionPlanMarkdown(actions: ReportAction[], evidence: ReportEvidence[], repositoryUrl: string) {
  const sources = new Map(evidence.map(item => [item.id, item]));
  return ["## План действий", "", ...actions.flatMap((action, index) => [
    `### ${index + 1}. ${action.title}`, "",
    `Приоритет: ${priorityLabel(action.priority)}.`, "",
    `**Основание:** ${action.reason}`, "",
    `**Файлы:** ${action.paths.map(path => `\`${path}\``).join(", ")}`, "",
    `**Проверка результата:** ${action.verification}`, "",
    `**Источники:** ${action.evidenceIds.map(id => {
      const source = sources.get(id);
      const url = source?.commitHash && source.kind === "COMMIT" ? commitUrl(repositoryUrl, source.commitHash) : null;
      return url ? `[${id} · ${source?.commitHash?.slice(0, 10)}](${url})` : id;
    }).join(", ")}`, "",
  ])].join("\n");
}

export function actionPlanJson(actions: ReportAction[], evidence: ReportEvidence[], reportId: number) {
  const referenced = new Set(actions.flatMap(action => action.evidenceIds));
  return JSON.stringify({ reportId, actions, evidence: evidence.filter(source => referenced.has(source.id)) }, null, 2);
}

export function priorityLabel(priority: ReportAction["priority"]) {
  return { HIGH: "Высокий", MEDIUM: "Средний", LOW: "Низкий" }[priority];
}
