import { ArrowUpRight, History, RotateCcw, Trash2 } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { FlowHoverButton } from "@/components/ui/flow-hover-button";
import { taskStatusLabel } from "@/lib/labels";
import type { AnalysisTask } from "@/lib/types";
import { cn, repositoryName } from "@/lib/utils";

interface RecentAnalysesProps {
  className?: string;
  hasHiddenTasks: boolean;
  onClear: () => void;
  onOpen: (task: AnalysisTask) => void;
  onRestore: () => void;
  tasks: AnalysisTask[];
}

export function RecentAnalyses({
  className,
  hasHiddenTasks,
  onClear,
  onOpen,
  onRestore,
  tasks,
}: RecentAnalysesProps) {
  const visibleTasks = tasks;

  return (
    <section aria-labelledby="history-title" className={cn("mt-8", className)}>
      <div className="mb-3 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-muted">
          <History className="size-3.5" />
          <h2 id="history-title">Последние анализы</h2>
        </div>
        {visibleTasks.length > 0 && (
          <button
            aria-label="Очистить историю анализов"
            className="flex items-center gap-1 text-[10px] font-medium text-muted transition-colors hover:text-ink"
            onClick={onClear}
            type="button"
          >
            <Trash2 className="size-3" /> Очистить
          </button>
        )}
      </div>

      <AnimatePresence initial={false} mode="popLayout">
        {visibleTasks.length > 0 ? (
          <motion.div
            animate={{ opacity: 1, height: "auto" }}
            className="max-h-44 space-y-1.5 overflow-y-auto pr-1 lg:min-h-0 lg:max-h-none lg:flex-1"
            exit={{ opacity: 0, height: 0 }}
            initial={{ opacity: 0, height: 0 }}
            key="history-list"
          >
            {visibleTasks.map((item) => (
              <button
                className="group flex w-full items-center justify-between gap-2 rounded-xl px-2.5 py-2 text-left transition-colors hover:bg-panel"
                key={item.id}
                onClick={() => onOpen(item)}
                type="button"
              >
                <span className="min-w-0">
                  <span className="block truncate text-xs font-medium text-ink">
                    {repositoryName(item.repositoryUrl)}
                  </span>
                  <span className="block text-[10px] text-muted">
                    {item.branch} · {taskStatusLabel(item.status).toLowerCase()}
                  </span>
                </span>
                <ArrowUpRight className="size-3.5 shrink-0 text-muted opacity-0 transition-opacity group-hover:opacity-100" />
              </button>
            ))}
          </motion.div>
        ) : (
          <motion.div
            animate={{ opacity: 1, y: 0 }}
            className="rounded-xl border border-line bg-panel/45 p-3"
            initial={{ opacity: 0, y: -4 }}
            key="empty-history"
          >
            <p className="text-xs leading-5 text-muted">
              {hasHiddenTasks
                ? "История скрыта на этом устройстве."
                : "Здесь появятся недавно запущенные анализы."}
            </p>
            {hasHiddenTasks && (
              <FlowHoverButton
                className="mt-2 px-2.5 py-1.5 text-[11px]"
                icon={<RotateCcw className="size-3" />}
                onClick={onRestore}
                type="button"
              >
                Вернуть историю
              </FlowHoverButton>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </section>
  );
}
