import {
  AlertCircle,
  BookOpenText,
  CheckCircle2,
  GitFork,
  Moon,
  RotateCcw,
  Square,
  Sparkles,
  Sun,
} from "lucide-react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import {
  type MouseEvent as ReactMouseEvent,
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import { flushSync } from "react-dom";
import { AnalysisTimeline } from "@/components/AnalysisTimeline";
import { GitCredentialsFields } from "@/components/GitCredentialsFields";
import { HelpAccordion } from "@/components/HelpAccordion";
import { InsightsPanel } from "@/components/InsightsPanel";
import { RecentAnalyses } from "@/components/RecentAnalyses";
import {
  RepositoryForm,
  type AnalysisRequest,
} from "@/components/RepositoryForm";
import { ReportPanel } from "@/components/ReportPanel";
import { ReportPeriodPicker } from "@/components/report/ReportPeriodPicker";
import { AudienceSelect } from "@/components/ui/audience-select";
import { FlowHoverButton } from "@/components/ui/flow-hover-button";
import { api } from "@/lib/api";
import { ALL_TIME_PERIOD, periodKey } from "@/lib/reportPeriod";
import type {
  AnalysisProgress,
  AnalysisReport,
  AnalysisTask,
  AnalysisTaskStatus,
  CommitClusters,
  CommitGraph,
  CommitInsight,
  GitTag,
  ReportAudience,
  ReportEvent,
  ReportPeriod,
  ReportStatus,
  RepositoryFacts,
} from "@/lib/types";
import { repositoryName } from "@/lib/utils";

type Theme = "light" | "dark";
type ConnectionStatus = "checking" | "online" | "offline";
type ThemeRipple = {
  color: string;
  nextTheme: Theme;
  radius: number;
  x: number;
  y: number;
};

const hiddenHistoryKey = "repository-analyzer-hidden-history";
const terminalReportStatuses: ReportStatus[] = [
  "COMPLETED",
  "FAILED",
  "CANCELLED",
];
const reportEventNames = [
  "snapshot",
  "started",
  "progress",
  "synthesis",
  "completed",
  "failed",
  "cancelled",
];
const cancellableTaskStatuses: AnalysisTaskStatus[] = [
  "NEW",
  "CLONING",
  "DISCOVERING_COMMITS",
  "PROCESSING",
];

interface ThemeViewTransition {
  ready: Promise<void>;
}

type ViewTransitionDocument = Document & {
  startViewTransition?: (callback: () => void) => ThemeViewTransition;
};

type ViewTransitionAnimationOptions = KeyframeAnimationOptions & {
  pseudoElement: string;
};

function initialTheme(): Theme {
  const saved = localStorage.getItem("repository-analyzer-theme");
  if (saved === "light" || saved === "dark") return saved;
  return window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
}

function initialHiddenTaskIds() {
  try {
    const saved = JSON.parse(localStorage.getItem(hiddenHistoryKey) ?? "[]");
    return Array.isArray(saved)
      ? saved.filter((id): id is number => Number.isInteger(id))
      : [];
  } catch {
    return [];
  }
}

export default function App() {
  const reduceMotion = useReducedMotion();
  const [theme, setTheme] = useState<Theme>(initialTheme);
  const [retryGitUsername, setRetryGitUsername] = useState("");
  const [retryGitAccessToken, setRetryGitAccessToken] = useState("");
  const [themeRipple, setThemeRipple] = useState<ThemeRipple | null>(null);
  const [tasks, setTasks] = useState<AnalysisTask[]>([]);
  const [hiddenTaskIds, setHiddenTaskIds] =
    useState<number[]>(initialHiddenTaskIds);
  const [task, setTask] = useState<AnalysisTask | null>(null);
  const [progress, setProgress] = useState<AnalysisProgress | null>(null);
  const [graph, setGraph] = useState<CommitGraph | null>(null);
  const [clusters, setClusters] = useState<CommitClusters | null>(null);
  const [report, setReport] = useState<AnalysisReport | null>(null);
  const [facts, setFacts] = useState<RepositoryFacts | null>(null);
  const [commitInsights, setCommitInsights] = useState<CommitInsight[]>([]);
  const [audience, setAudience] = useState<ReportAudience>("DEVELOPER");
  const [period, setPeriod] = useState<ReportPeriod>(ALL_TIME_PERIOD);
  const [tags, setTags] = useState<GitTag[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancellingReport, setCancellingReport] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [connectionStatus, setConnectionStatus] =
    useState<ConnectionStatus>("checking");
  const reportRequestKey = useRef("");
  const restartingRecoveredReportId = useRef<number | null>(null);
  const taskId = task?.id ?? null;
  const taskStatus = task?.status ?? null;
  const reportId = report?.id ?? null;
  const reportStatus = report?.status ?? null;

  useLayoutEffect(() => {
    document.documentElement.classList.toggle("dark", theme === "dark");
    document.documentElement.style.colorScheme = theme;
    localStorage.setItem("repository-analyzer-theme", theme);
  }, [theme]);

  useEffect(() => {
    localStorage.setItem(hiddenHistoryKey, JSON.stringify(hiddenTaskIds));
  }, [hiddenTaskIds]);

  const refreshTasks = useCallback(async () => {
    try {
      setTasks(await api.listTasks());
      setConnectionStatus("online");
    } catch {
      setConnectionStatus("offline");
    }
  }, []);

  useEffect(() => {
    let disposed = false;
    void api
      .listTasks()
      .then((nextTasks) => {
        if (!disposed) {
          setTasks(nextTasks);
          setConnectionStatus("online");
        }
      })
      .catch(() => {
        if (!disposed) setConnectionStatus("offline");
      });

    const timer = setInterval(() => {
      void api
        .listTasks()
        .then((nextTasks) => {
          if (!disposed) {
            setTasks(nextTasks);
            setConnectionStatus("online");
          }
        })
        .catch(() => {
          if (!disposed) setConnectionStatus("offline");
        });
    }, 10_000);

    return () => {
      disposed = true;
      clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    const cancellationSettled =
      taskStatus === "CANCELLED" &&
      (progress?.processingCommits ?? 0) === 0;

    if (
      !taskId ||
      taskStatus === "COMPLETED" ||
      taskStatus === "FAILED" ||
      cancellationSettled
    ) {
      return;
    }

    let disposed = false;
    const poll = async () => {
      try {
        const [freshTask, freshProgress] = await Promise.all([
          api.getTask(taskId),
          api.getProgress(taskId),
        ]);
        if (disposed) return;
        setTask(freshTask);
        setProgress(freshProgress);
        if (
          freshTask.status === "COMPLETED" ||
          freshTask.status === "FAILED" ||
          (freshTask.status === "CANCELLED" &&
            freshProgress.processingCommits === 0)
        ) {
          clearInterval(timer);
          void refreshTasks();
        }
      } catch (caught) {
        if (!disposed) setError(errorMessage(caught));
      }
    };

    const timer = setInterval(() => void poll(), 1200);
    void poll();
    return () => {
      disposed = true;
      clearInterval(timer);
    };
  }, [taskId, taskStatus, progress?.processingCommits, refreshTasks]);

  useEffect(() => {
    if (!taskId || taskStatus !== "COMPLETED") return;
    const key = `${taskId}:${audience}:${periodKey(period)}`;
    if (reportRequestKey.current === key) return;
    reportRequestKey.current = key;

    let disposed = false;
    const prepareResults = async () => {
      try {
        const [nextGraph, nextClusters, nextReport] = await Promise.all([
          api.getGraph(taskId),
          api.getClusters(taskId),
          api.createReport(taskId, audience, period),
        ]);
        // Показатели и коммиты — в границах периода отчёта.
        const [nextFacts, nextInsights] = await Promise.all([
          api.getReportFacts(nextReport.id),
          api.getReportCommitInsights(nextReport.id),
        ]);
        if (disposed) return;
        setGraph(nextGraph);
        setClusters(nextClusters);
        setFacts(nextFacts);
        setCommitInsights(nextInsights);
        setReport(nextReport);
      } catch (caught) {
        if (!disposed) {
          reportRequestKey.current = "";
          setError(errorMessage(caught));
        }
      }
    };

    void prepareResults();
    return () => {
      disposed = true;
    };
  }, [taskId, taskStatus, audience, period]);

  useEffect(() => {
    if (!taskId || taskStatus !== "COMPLETED") return;
    let disposed = false;
    void api
      .getTags(taskId)
      .then((nextTags) => {
        if (!disposed) setTags(nextTags);
      })
      .catch(() => undefined);
    return () => {
      disposed = true;
    };
  }, [taskId, taskStatus]);

  const refreshReport = useCallback(async (reportId: number) => {
    const fresh = await api.getReport(reportId);
    setReport(fresh);
    if (terminalReportStatuses.includes(fresh.status)) {
      const [nextFacts, nextInsights] = await Promise.all([
        api.getReportFacts(fresh.id),
        api.getReportCommitInsights(fresh.id),
      ]);
      setFacts(nextFacts);
      setCommitInsights(nextInsights);
    }
    return fresh;
  }, []);

  useEffect(() => {
    if (
      !report ||
      report.status !== "NEW" ||
      taskStatus !== "COMPLETED" ||
      restartingRecoveredReportId.current === report.id
    ) {
      return;
    }

    restartingRecoveredReportId.current = report.id;
    let disposed = false;
    void api
      .createReport(report.analysisTaskId, report.audience, report.period)
      .then((restarted) => {
        if (disposed) return;
        if (restarted.status !== "NEW") {
          restartingRecoveredReportId.current = null;
        }
        setReport(restarted);
      })
      .catch((caught) => {
        if (disposed) return;
        restartingRecoveredReportId.current = null;
        setError(errorMessage(caught));
      });

    return () => {
      disposed = true;
    };
  }, [report, taskStatus]);

  useEffect(() => {
    if (
      !reportId ||
      reportStatus === "COMPLETED" ||
      reportStatus === "FAILED" ||
      reportStatus === "CANCELLED"
    ) {
      return;
    }

    const source = new EventSource(api.reportEventsUrl(reportId));
    let fallbackTimer: ReturnType<typeof setInterval> | null = null;

    const handleEvent = (message: MessageEvent<string>) => {
      const event = JSON.parse(message.data) as ReportEvent;
      setReport((current) =>
        current && current.id === event.reportId
          ? {
              ...current,
              processedCommits: event.processedCommits,
              totalCommits: event.totalCommits || current.totalCommits,
            }
          : current,
      );

      if (event.commits?.length) {
        setCommitInsights((current) => mergeInsights(current, event.commits));
      }
      if (event.type === "SNAPSHOT") {
        // После (пере)подключения догружаем то, что могло прийти без нас.
        void refreshReport(event.reportId).catch(() => undefined);
        void api
          .getReportCommitInsights(event.reportId)
          .then(setCommitInsights)
          .catch(() => undefined);
      } else if (event.status && terminalReportStatuses.includes(event.status)) {
        void refreshReport(event.reportId).catch(() => undefined);
      }
    };

    reportEventNames.forEach((eventName) =>
      source.addEventListener(eventName, handleEvent),
    );

    // Опрос нужен только если SSE-соединение закрыто окончательно.
    source.onerror = () => {
      if (source.readyState === EventSource.CLOSED && !fallbackTimer) {
        fallbackTimer = setInterval(() => {
          void refreshReport(reportId).catch(() => undefined);
        }, 4000);
      }
    };

    return () => {
      if (fallbackTimer) clearInterval(fallbackTimer);
      source.close();
    };
  }, [reportId, reportStatus, refreshReport]);

  async function startAnalysis(request: AnalysisRequest) {
    setSubmitting(true);
    setError(null);
    setAudience(request.audience);
    setPeriod(ALL_TIME_PERIOD);
    setTags([]);
    setProgress(null);
    setGraph(null);
    setClusters(null);
    setReport(null);
    setFacts(null);
    setCommitInsights([]);
    reportRequestKey.current = "";

    try {
      const created = await api.createTask(
        request.repositoryUrl,
        request.branch,
        request.gitUsername,
        request.gitAccessToken,
      );
      setTask(created);
      setHiddenTaskIds((current) => current.filter((id) => id !== created.id));
      void refreshTasks();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  async function retryTask() {
    if (!task) return;
    setError(null);
    try {
      const retried = await api.retryTask(
        task.id,
        retryGitUsername.trim() || undefined,
        retryGitAccessToken || undefined,
      );
      setRetryGitAccessToken("");
      setTask(retried);
      setGraph(null);
      setClusters(null);
      setReport(null);
      setFacts(null);
      setCommitInsights([]);
      reportRequestKey.current = "";
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  async function cancelTask() {
    if (!task) return;
    setCancelling(true);
    setError(null);

    try {
      const cancelled = await api.cancelTask(task.id);
      setTask(cancelled);
      setReport(null);
      reportRequestKey.current = "";
      void refreshTasks();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setCancelling(false);
    }
  }

  async function retryReport() {
    if (!report) return;
    setError(null);
    try {
      const retried = await api.retryReport(report.id);
      setReport(retried);
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  async function cancelReport() {
    if (!report || report.status !== "PROCESSING") return;
    setCancellingReport(true);
    setError(null);
    try {
      const cancelled = await api.cancelReport(report.id);
      setReport(cancelled);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setCancellingReport(false);
    }
  }

  async function openTask(selected: AnalysisTask) {
    setTask(selected);
    setPeriod(ALL_TIME_PERIOD);
    setTags([]);
    setProgress(null);
    setGraph(null);
    setClusters(null);
    setReport(null);
    setFacts(null);
    setCommitInsights([]);
    setError(null);
    reportRequestKey.current = "";
    try {
      setProgress(await api.getProgress(selected.id));
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }

  function selectReportAudience(nextAudience: ReportAudience) {
    if (nextAudience === audience) return;
    setAudience(nextAudience);
    setReport(null);
    reportRequestKey.current = "";
  }

  function selectReportPeriod(nextPeriod: ReportPeriod) {
    if (periodKey(nextPeriod) === periodKey(period)) return;
    setPeriod(nextPeriod);
    setReport(null);
    setError(null);
    reportRequestKey.current = "";
  }

  function toggleTheme(event: ReactMouseEvent<HTMLButtonElement>) {
    if (themeRipple) return;
    const nextTheme = theme === "light" ? "dark" : "light";
    const startViewTransition = (document as ViewTransitionDocument)
      .startViewTransition;
    if (reduceMotion) {
      setTheme(nextTheme);
      return;
    }

    const x = event.clientX;
    const y = event.clientY;
    const radius = Math.hypot(
      Math.max(x, window.innerWidth - x),
      Math.max(y, window.innerHeight - y),
    );

    if (!startViewTransition) {
      setThemeRipple({
        color: nextTheme === "dark" ? "#050607" : "#e7e9ec",
        nextTheme,
        radius,
        x,
        y,
      });
      return;
    }

    const transition = startViewTransition.call(document, () => {
      flushSync(() => setTheme(nextTheme));
    });

    void transition.ready
      .then(() => {
        document.documentElement.animate(
          {
            clipPath: [
              `circle(0px at ${x}px ${y}px)`,
              `circle(${radius}px at ${x}px ${y}px)`,
            ],
          },
          {
            duration: 720,
            easing: "cubic-bezier(0.22, 1, 0.36, 1)",
            pseudoElement: "::view-transition-new(root)",
          } as ViewTransitionAnimationOptions,
        );
      })
      .catch(() => undefined);
  }

  const hasWorkspace = Boolean(task);
  const insightsReady = Boolean(graph && clusters);
  const visibleTasks = tasks.filter((item) =>
    !hiddenTaskIds.includes(item.id),
  );
  const hasHiddenTasks = tasks.some((item) =>
    hiddenTaskIds.includes(item.id),
  );

  return (
    <div className="tech-canvas min-h-screen text-ink">
      <AnimatePresence initial={!reduceMotion}>
        {themeRipple && (
          <motion.div
            animate={{ opacity: 1, scale: 1 }}
            aria-hidden="true"
            className="pointer-events-none fixed z-[10000] rounded-full"
            exit={{ opacity: 0 }}
            initial={{ opacity: 1, scale: 0 }}
            onAnimationComplete={() => {
              if (theme === themeRipple.nextTheme) return;
              flushSync(() => setTheme(themeRipple.nextTheme));
              window.requestAnimationFrame(() => setThemeRipple(null));
            }}
            style={{
              backgroundColor: themeRipple.color,
              height: themeRipple.radius * 2,
              left: themeRipple.x - themeRipple.radius,
              top: themeRipple.y - themeRipple.radius,
              width: themeRipple.radius * 2,
            }}
            transition={
              reduceMotion
                ? { duration: 0 }
                : { duration: 0.68, ease: [0.22, 1, 0.36, 1] }
            }
          />
        )}
      </AnimatePresence>
      <div className="mx-auto grid min-h-screen max-w-[1680px] lg:grid-cols-[310px_minmax(0,1fr)]">
        <motion.aside
          animate={{ opacity: 1, x: 0 }}
          className="flex flex-col border-b border-line bg-sidebar px-6 py-6 lg:sticky lg:top-0 lg:h-screen lg:border-b-0 lg:border-r lg:px-7 lg:py-8"
          initial={reduceMotion ? false : { opacity: 0, x: -24 }}
          transition={
            reduceMotion
              ? { duration: 0 }
              : { duration: 0.55, ease: [0.22, 1, 0.36, 1] }
          }
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="relative flex size-10 items-center justify-center rounded-2xl border border-line bg-panel shadow-soft">
                <GitFork className="size-5 text-accent" />
                <Sparkles className="absolute -right-1 -top-1 size-3.5 text-accent" />
              </span>
              <div>
                <p className="font-display text-[15px] font-semibold leading-tight">
                  Анализатор
                </p>
                <p className="text-xs text-muted">репозиториев</p>
              </div>
            </div>
            <button
              aria-label={`Включить ${theme === "light" ? "тёмную" : "светлую"} тему`}
              className="icon-button"
              disabled={Boolean(themeRipple)}
              onClick={toggleTheme}
              type="button"
            >
              {theme === "light" ? (
                <Moon className="size-4" />
              ) : (
                <Sun className="size-4" />
              )}
            </button>
          </div>

          <RecentAnalyses
            className="hidden min-h-0 flex-1 lg:flex lg:flex-col"
            hasHiddenTasks={hasHiddenTasks}
            onClear={() =>
              setHiddenTaskIds((current) => [
                ...new Set([...current, ...tasks.map((item) => item.id)]),
              ])
            }
            onOpen={(item) => void openTask(item)}
            onRestore={() => setHiddenTaskIds([])}
            tasks={visibleTasks}
          />

          <div className="mt-auto hidden max-h-[46vh] min-h-0 overflow-y-auto pr-1 lg:block">
            <HelpAccordion />
          </div>

          <div className="diagonal-card mt-8 hidden shrink-0 lg:mt-6 lg:block">
            <p className="text-sm font-semibold text-ink">dev. Ivan Naumenko</p>
            <p className="mt-4 text-[10px] uppercase tracking-[0.16em] text-muted">
              © 2026 Иван Науменко
            </p>
          </div>
        </motion.aside>

        <motion.main
          animate={{ opacity: 1 }}
          className="relative overflow-x-clip px-4 py-5 sm:px-7 sm:py-7 xl:px-12 xl:py-10"
          initial={reduceMotion ? false : { opacity: 0 }}
          transition={{ duration: reduceMotion ? 0 : 0.5 }}
        >
          <div className="relative mx-auto max-w-[1180px]">
            <motion.header
              animate={{ opacity: 1, y: 0 }}
              className="mb-8 flex items-center justify-between gap-4"
              initial={reduceMotion ? false : { opacity: 0, y: -10 }}
              transition={{
                delay: reduceMotion ? 0 : 0.14,
                duration: reduceMotion ? 0 : 0.4,
              }}
            >
              <div className="flex items-center gap-2 text-xs font-medium text-muted">
                <span className="relative flex size-2">
                  {connectionStatus === "online" && (
                    <span className="absolute inline-flex size-full animate-ping rounded-full bg-success opacity-40 motion-reduce:animate-none" />
                  )}
                  <span
                    className={`relative inline-flex size-2 rounded-full ${
                      connectionStatus === "online"
                        ? "bg-success"
                        : connectionStatus === "offline"
                          ? "bg-red-400"
                          : "bg-amber-400"
                    }`}
                  />
                </span>
                {connectionStatus === "online"
                  ? "Сервер подключён"
                  : connectionStatus === "offline"
                    ? "Сервер недоступен"
                    : "Проверяем соединение"}
              </div>
              {task && (
                <span className="hidden rounded-full border border-line bg-panel/70 px-3 py-1.5 font-mono text-[10px] text-muted sm:inline">
                  задача/{task.id} · {task.branch}
                </span>
              )}
            </motion.header>

            <motion.section
              animate={{ opacity: 1, scale: 1, y: 0 }}
              className="hero-card"
              initial={
                reduceMotion ? false : { opacity: 0, scale: 0.985, y: 16 }
              }
              transition={
                reduceMotion
                  ? { duration: 0 }
                  : {
                      delay: 0.08,
                      duration: 0.62,
                      ease: [0.22, 1, 0.36, 1],
                    }
              }
            >
              <div aria-hidden="true" className="glass-orb" />
              <div className="relative z-10 w-full">
                <p className="eyebrow mb-4">Новый анализ</p>
                <RepositoryForm loading={submitting} onSubmit={startAnalysis} />
              </div>
            </motion.section>

            <div className="soft-panel mt-4 px-5 pb-5 lg:hidden">
              <RecentAnalyses
                hasHiddenTasks={hasHiddenTasks}
                onClear={() =>
                  setHiddenTaskIds((current) => [
                    ...new Set([...current, ...tasks.map((item) => item.id)]),
                  ])
                }
                onOpen={(item) => void openTask(item)}
                onRestore={() => setHiddenTaskIds([])}
                tasks={visibleTasks}
              />
            </div>

            <AnimatePresence initial={!reduceMotion}>
              {error && (
                <motion.div
                  animate={{ opacity: 1, y: 0 }}
                  className="mt-4 flex items-start gap-3 rounded-2xl border border-red-300/60 bg-red-50/80 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300"
                  exit={{ opacity: 0, y: -5 }}
                  initial={reduceMotion ? false : { opacity: 0, y: -5 }}
                  transition={reduceMotion ? { duration: 0 } : undefined}
                >
                  <AlertCircle className="mt-0.5 size-4 shrink-0" />
                  <span className="flex-1">{error}</span>
                  <button onClick={() => setError(null)} type="button">
                    ×
                  </button>
                </motion.div>
              )}
            </AnimatePresence>

            {task && (
              <motion.div
                animate={{ opacity: 1, y: 0 }}
                className="mt-5 space-y-4"
                initial={reduceMotion ? false : { opacity: 0, y: 12 }}
                transition={reduceMotion ? { duration: 0 } : undefined}
              >
                <div className="grid gap-4">
                  <section className="soft-panel p-5 sm:p-6">
                    <div>
                      <div className="flex flex-wrap items-start justify-between gap-4">
                        <div>
                          <p className="eyebrow">Текущий репозиторий</p>
                          <h2 className="mt-1.5 max-w-xl break-words font-display text-2xl font-semibold text-ink">
                            {repositoryName(task.repositoryUrl)}
                          </h2>
                          <p className="mt-1 break-all font-mono text-xs text-muted">
                            {task.repositoryUrl}
                          </p>
                          {task.status === "COMPLETED" && (
                            <div className="mt-4 w-full max-w-sm">
                              <p className="mb-2 text-[10px] font-semibold uppercase tracking-[0.16em] text-muted">
                                Версия отчёта
                              </p>
                              <AudienceSelect
                                onChange={selectReportAudience}
                                placement="bottom"
                                value={audience}
                              />
                              <p className="mb-2 mt-4 text-[10px] font-semibold uppercase tracking-[0.16em] text-muted">
                                Период
                              </p>
                              <ReportPeriodPicker
                                key={task.id}
                                onChange={selectReportPeriod}
                                tags={tags}
                                value={period}
                              />
                              <p className="mt-2 text-[10px] leading-4 text-muted">
                                Для каждой роли и периода создаётся отдельный отчёт.
                              </p>
                            </div>
                          )}
                        </div>
                        {task.status === "COMPLETED" && (
                          <span className="flex items-center gap-1.5 rounded-full bg-success-soft px-3 py-1.5 text-xs font-medium text-success">
                            <CheckCircle2 className="size-3.5" /> Готово
                          </span>
                        )}
                      </div>

                      {progress && (
                        <div className="mt-5 grid grid-cols-2 gap-2.5 sm:grid-cols-4">
                          {[
                            ["Всего", progress.totalCommits],
                            ["Готово", progress.completedCommits],
                            ["В работе", progress.processingCommits],
                            ["С ошибкой", progress.failedCommits],
                          ].map(([label, value]) => (
                            <div className="rounded-xl border border-line bg-panel-strong p-3" key={label}>
                              <div className="font-display text-xl font-semibold text-ink">{value}</div>
                              <div className="mt-0.5 text-[11px] text-muted">{label}</div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>

                    {cancellableTaskStatuses.includes(task.status) && (
                      <FlowHoverButton
                        className="mt-6 self-start border-red-300 text-red-700 before:bg-red-700 hover:text-white dark:border-red-900/70 dark:text-red-300 dark:before:bg-red-300 dark:hover:text-red-950"
                        disabled={cancelling}
                        icon={<Square className="size-3.5 fill-current" />}
                        onClick={() => void cancelTask()}
                        type="button"
                      >
                        {cancelling ? "Останавливаем…" : "Остановить анализ"}
                      </FlowHoverButton>
                    )}

                    {(task.status === "FAILED" ||
                      task.status === "CANCELLED") && (
                      <div className="mt-6 max-w-2xl space-y-3">
                        {task.status === "CANCELLED" && (
                          <p className="text-xs leading-5 text-muted">
                            Новые коммиты больше не запускаются. Возобновление
                            станет доступно после завершения уже начатых
                            операций.
                          </p>
                        )}
                        <GitCredentialsFields
                          accessToken={retryGitAccessToken}
                          disabled={
                            task.status === "CANCELLED" &&
                            (progress?.processingCommits ?? 0) > 0
                          }
                          onAccessTokenChange={setRetryGitAccessToken}
                          onUsernameChange={setRetryGitUsername}
                          username={retryGitUsername}
                        />
                        <FlowHoverButton
                          className="self-start"
                          disabled={
                            task.status === "CANCELLED" &&
                            (progress?.processingCommits ?? 0) > 0
                          }
                          icon={<RotateCcw className="size-4" />}
                          onClick={() => void retryTask()}
                          type="button"
                        >
                          {task.status === "CANCELLED"
                            ? (progress?.processingCommits ?? 0) > 0
                              ? "Завершаем текущие операции…"
                              : "Возобновить анализ"
                            : "Повторить анализ"}
                        </FlowHoverButton>
                      </div>
                    )}
                  </section>

                  <AnalysisTimeline
                    insightsReady={insightsReady}
                    progress={progress}
                    report={report}
                    task={task}
                  />
                </div>

                {graph && clusters && (
                  <InsightsPanel clusters={clusters} graph={graph} />
                )}

                <ReportPanel
                  cancelling={cancellingReport}
                  commits={commitInsights}
                  facts={facts}
                  onCancel={cancelReport}
                  onRetry={retryReport}
                  report={report}
                />
              </motion.div>
            )}

            {!hasWorkspace && (
              <div className="mx-auto mt-7 grid max-w-4xl gap-3 sm:grid-cols-3">
                {[
                  [BookOpenText, "Технический контекст", "Diff и структура Java-кода до обращения к LLM"],
                  [GitFork, "Настоящая история", "Родители, ветки и смысловые группы изменений"],
                  [Sparkles, "Ролевые отчёты", "Пять вариантов отчёта для пяти точек зрения"],
                ].map(([Icon, title, description], index) => {
                  const FeatureIcon = Icon as typeof BookOpenText;
                  return (
                    <motion.div
                      animate={{ opacity: 1, y: 0 }}
                      className="tech-card rounded-2xl border border-line/80 bg-panel/45 p-4"
                      initial={reduceMotion ? false : { opacity: 0, y: 12 }}
                      key={String(title)}
                      transition={{
                        delay: reduceMotion ? 0 : 0.2 + index * 0.07,
                        duration: reduceMotion ? 0 : 0.4,
                        ease: [0.22, 1, 0.36, 1],
                      }}
                    >
                      <FeatureIcon className="mb-3 size-4 text-accent opacity-65" />
                      <p className="text-sm font-semibold text-ink">{String(title)}</p>
                      <p className="mt-1 text-xs leading-5 text-muted">{String(description)}</p>
                    </motion.div>
                  );
                })}
              </div>
            )}

            <div className="soft-panel mt-5 px-5 pb-5 lg:hidden">
              <HelpAccordion />
            </div>

            <div className="diagonal-card mx-auto mt-5 max-w-4xl lg:hidden">
              <p className="text-sm font-semibold text-ink">dev. Ivan Naumenko</p>
              <p className="mt-4 text-[10px] uppercase tracking-[0.16em] text-muted">
                © 2026 Иван Науменко
              </p>
            </div>
          </div>
        </motion.main>
      </div>
    </div>
  );
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "Произошла непредвиденная ошибка";
}

function mergeInsights(current: CommitInsight[], incoming: CommitInsight[]) {
  const byCommit = new Map(current.map((item) => [item.commitId, item]));
  incoming.forEach((item) => byCommit.set(item.commitId, item));
  return [...byCommit.values()].sort((left, right) => left.position - right.position);
}
