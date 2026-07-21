import { Check, LoaderCircle, X } from "lucide-react";
import type {
  AnalysisProgress,
  AnalysisReport,
  AnalysisTask,
} from "@/lib/types";
import { taskStatusLabel } from "@/lib/labels";
import { cn } from "@/lib/utils";

interface AnalysisTimelineProps {
  task: AnalysisTask | null;
  progress: AnalysisProgress | null;
  report: AnalysisReport | null;
  insightsReady: boolean;
}

type StepState = "waiting" | "active" | "complete" | "failed";

export function AnalysisTimeline({
  task,
  progress,
  report,
  insightsReady,
}: AnalysisTimelineProps) {
  const technicalStatus = task?.status;
  const steps = [
    {
      title: "Подготовка репозитория",
      detail: "Клонирование или обновление выбранной ветки",
      state: stateFor(1),
    },
    {
      title: "Поиск коммитов",
      detail: "Восстановление истории и родительских связей",
      state: stateFor(2),
    },
    {
      title: "Анализ изменений",
      detail: progress?.totalCommits
        ? `Обработано коммитов: ${progress.completedCommits} из ${progress.totalCommits}`
        : "Diff и структура Java-кода обрабатываются в 10 потоках",
      state: stateFor(3),
    },
    {
      title: "Построение выводов",
      detail: "Метрики графа и смысловые группы изменений",
      state: stateFor(4),
    },
    {
      title: "Формирование отчёта",
      detail: report?.totalCommits
        ? `Учтено коммитов: ${report.processedCommits} из ${report.totalCommits}`
        : "Отчёт с учётом выбранной роли пользователя",
      state: stateFor(5),
    },
  ];

  function stateFor(step: number): StepState {
    if (!task) return "waiting";
    if (technicalStatus === "FAILED" && step <= 3) {
      return step === 3 ? "failed" : "complete";
    }

    if (step === 1) {
      return technicalStatus === "NEW" || technicalStatus === "CLONING"
        ? "active"
        : "complete";
    }
    if (step === 2) {
      if (technicalStatus === "DISCOVERING_COMMITS") return "active";
      return ["PROCESSING", "COMPLETED"].includes(technicalStatus || "")
        ? "complete"
        : "waiting";
    }
    if (step === 3) {
      if (technicalStatus === "PROCESSING") return "active";
      return technicalStatus === "COMPLETED" ? "complete" : "waiting";
    }
    if (step === 4) {
      if (insightsReady) return "complete";
      return technicalStatus === "COMPLETED" ? "active" : "waiting";
    }
    if (report?.status === "FAILED") return "failed";
    if (report?.status === "COMPLETED") return "complete";
    if (report?.status === "PROCESSING" || report?.status === "NEW") {
      return "active";
    }
    return "waiting";
  }

  const percentage = progress?.totalCommits
    ? Math.round((progress.completedCommits / progress.totalCommits) * 100)
    : 0;

  return (
    <section className="soft-panel p-5 sm:p-6">
      <div className="mb-6 flex items-center justify-between gap-4">
        <div>
          <p className="eyebrow">Ход выполнения</p>
          <h2 className="mt-1 font-display text-xl font-semibold text-ink">
            Этапы анализа
          </h2>
        </div>
        <span className="rounded-full border border-line bg-panel-strong px-3 py-1 text-xs font-medium text-muted">
          {task ? taskStatusLabel(task.status) : "Готов к запуску"}
        </span>
      </div>

      <ol>
        {steps.map((step, index) => (
          <li className="relative flex gap-4 pb-5 last:pb-0" key={step.title}>
            {index < steps.length - 1 && (
              <span className="absolute left-[15px] top-8 h-[calc(100%-1rem)] w-px bg-line" />
            )}
            <span
              className={cn(
                "relative z-10 flex size-8 shrink-0 items-center justify-center rounded-full border text-xs font-semibold transition-colors",
                step.state === "complete" &&
                  "border-accent bg-accent text-accent-contrast",
                step.state === "active" &&
                  "border-accent bg-panel text-accent shadow-[0_0_0_4px_var(--accent-soft)]",
                step.state === "waiting" &&
                  "border-line bg-panel text-muted",
                step.state === "failed" &&
                  "border-red-400 bg-red-50 text-red-600 dark:bg-red-950/30",
              )}
            >
              {step.state === "complete" ? (
                <Check className="size-4" />
              ) : step.state === "active" ? (
                <LoaderCircle className="size-4 animate-spin" />
              ) : step.state === "failed" ? (
                <X className="size-4" />
              ) : (
                index + 1
              )}
            </span>
            <div className="min-w-0 pt-0.5">
              <h3 className="text-sm font-semibold text-ink">{step.title}</h3>
              <p className="mt-0.5 text-xs leading-5 text-muted">{step.detail}</p>
              {index === 2 && step.state === "active" && progress && (
                <div className="mt-2 h-1.5 w-full max-w-52 overflow-hidden rounded-full bg-accent-soft">
                  <div
                    className="h-full rounded-full bg-accent transition-[width] duration-500"
                    style={{ width: `${percentage}%` }}
                  />
                </div>
              )}
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
