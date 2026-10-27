import type { ReportEvidence } from "@/lib/types";
import { formatDate } from "@/lib/utils";

export function describeEvidence(source: ReportEvidence): [string, string][] {
  try {
    const value = JSON.parse(source.description);
    if (source.kind === "FACTS") return [
      ["Коммиты", String(value.totalCommits)], ["Авторы", String(value.authorCount)],
      ["Изменённые файлы", String(value.touchedFiles)], ["Строки", `+${value.addedLines} / −${value.deletedLines}`],
      ["Период", `${formatDate(value.firstCommitAt)} — ${formatDate(value.lastCommitAt)}`],
      ["Высокий / средний риск", `${value.risks?.high ?? 0} / ${value.risks?.medium ?? 0}`],
    ];
    return [["Описание", value.description || "Описание не извлечено"], ["Стек", value.stack?.join(", ") || "Признаки не найдены"],
      ["Языки", value.languages?.map((item: { language: string; files: number }) => `${item.language} (${item.files})`).join(", ") || "Не определены"],
      ["Манифесты", value.manifests?.join(", ") || "Не обнаружены"],
      ["Покрытие анализа", value.analysisCoverage || "Текстовые признаки; отсутствие признака не доказывает отсутствие риска"],
      ["Основные / тестовые файлы", `${value.mainSourceFiles} / ${value.testSourceFiles}`],
      ["Контроллеры Spring", value.controllers?.join(", ") || "Признаки не найдены"],
      ["Признаки защиты", `Конфигурация: ${value.security?.securityConfiguration ? "найдена" : "не обнаружена"}; CSRF disable: ${value.security?.csrfDisabled ? "найден" : "не обнаружен"}; CORS *: ${value.security?.corsWildcard ? "найден" : "не обнаружен"}`],
      ["Секреты в конфигурации", value.security?.secretsInConfig?.join(", ") || "Признаки не найдены"],
      ["Ограничение сканирования", value.truncated ? "Достигнут лимит файлов" : "Лимит файлов не достигнут"]];
  } catch { return [["Данные", source.description]]; }
}
