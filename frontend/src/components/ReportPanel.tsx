import {
  AlertCircle,
  FileText,
  ListTree,
  LoaderCircle,
  RefreshCw,
  RotateCcw,
  Square,
} from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import { useMemo, useState } from "react";
import { ActionPlan } from "@/components/report/ActionPlan";
import { GroundedFindings, ReviewCoverage } from "@/components/report/GroundedFindings";
import { EvidencePanel } from "@/components/report/EvidencePanel";
import { CommitInsightList } from "@/components/report/CommitInsightList";
import { ExportMenu } from "@/components/report/ExportMenu";
import { KeyNumbers, ReportFacts } from "@/components/report/ReportFacts";
import { ReportMarkdown } from "@/components/report/ReportMarkdown";
import { FlowHoverButton } from "@/components/ui/flow-hover-button";
import { downloadBlob, safeFileNamePart } from "@/lib/download";
import { audienceLabel, reportTitle } from "@/lib/labels";
import { buildReportMarkdown } from "@/lib/markdownExport";
import { createReportPdfBlob } from "@/lib/pdf";
import { parseReportSections } from "@/lib/reportContent";
import { hasFactBlocks, reportLayout } from "@/lib/reportLayout";
import type {
  AnalysisReport,
  CommitInsight,
  RepositoryFacts,
} from "@/lib/types";
import { formatDate, formatDateTime, repositoryName } from "@/lib/utils";

interface ReportPanelProps {
  cancelling: boolean;
  report: AnalysisReport | null;
  facts: RepositoryFacts | null;
  commits: CommitInsight[];
  onCancel: () => Promise<void>;
  onRetry: () => Promise<void>;
}

const stopButtonClasses =
  "border-red-300 text-red-700 before:bg-red-700 hover:text-white dark:border-red-900/70 dark:text-red-300 dark:before:bg-red-300 dark:hover:text-red-950";

export function ReportPanel({
  cancelling,
  report,
  facts,
  commits,
  onCancel,
  onRetry,
}: ReportPanelProps) {
  const reduceMotion = useReducedMotion();
  const [exporting, setExporting] = useState(false);
  const [copied, setCopied] = useState(false);
  const [selectedEvidence, setSelectedEvidence] = useState<string | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);
  const content = report?.content ?? "";
  const parsed = useMemo(() => parseReportSections(content), [content]);

  if (!report) return null;

  const repositoryUrl = report.repositoryUrl;
  const projectName = repositoryName(repositoryUrl);
  const title = reportTitle(projectName, report.branch, report.audience);
  const audience = report.audience;
  const role = audienceLabel(audience);
  const layout = reportLayout(audience);
  const showFacts = facts !== null && hasFactBlocks(layout, facts);
  const inProgress = report.status === "NEW" || report.status === "PROCESSING";
  const commitsAnalyzed =
    report.totalCommits === 0 || report.processedCommits >= report.totalCommits;
  const percentage = report.totalCommits
    ? Math.round((report.processedCommits / report.totalCommits) * 100)
    : 0;
  const stage =
    report.status === "NEW"
      ? "Подготовка к формированию отчёта"
      : commitsAnalyzed
        ? `Формируются выводы для роли «${role}»`
        : `Анализ коммитов: ${report.processedCommits} из ${report.totalCommits}`;
  const statusText =
    report.status === "COMPLETED"
      ? `Сформирован ${formatDateTime(report.updatedAt)}`
      : report.status === "FAILED"
        ? "Формирование завершилось ошибкой"
        : report.status === "CANCELLED"
          ? "Формирование остановлено"
          : "Формируется";
  const dates =
    facts?.firstCommitAt && facts.lastCommitAt
      ? `коммиты с ${formatDate(facts.firstCommitAt)} по ${formatDate(facts.lastCommitAt)}`
      : null;
  const meta = [statusText, `версия анализа: ${report.analysisRevision}`, report.headCommitHash?.slice(0, 10), `период: ${report.period.label}`, dates]
    .filter(Boolean)
    .join(" · ");
  const summaryMarkdown = parsed.summary.map((item) => `- ${item}`).join("\n");
  const fileBaseName = [
    "report",
    safeFileNamePart(projectName),
    safeFileNamePart(report.branch),
    report.audience.toLowerCase(),
    report.period.type === "ALL" ? null : safeFileNamePart(periodSlug(report)),
  ]
    .filter(Boolean)
    .join("-");

  const toc = [
    ...(report.artifacts?.coverage ? [{ id: "report-coverage", title: "Покрытие проверки" }] : []),
    ...(parsed.summary.length > 0 ? [{ id: "report-summary", title: "Главное" }] : []),
    ...(facts ? [{ id: "report-numbers", title: "Ключевые показатели" }] : []),
    ...parsed.sections.filter(section => !report.artifacts?.coverage || section.title !== "Покрытие проверки").map((section) => ({ id: section.id, title: section.title })),
    ...(showFacts ? [{ id: "report-facts", title: "Показатели за период" }] : []),
    ...(report.artifacts ? [{ id: "report-actions", title: "План действий" }, { id: "report-evidence", title: "Источники" }] : []),
    { id: "report-commits", title: "Коммиты" },
  ];

  function markdown() {
    return buildReportMarkdown({
      title,
      subtitle: meta,
      audience,
      summary: parsed.summary,
      sections: parsed.sections,
      facts,
      commits,
      repositoryUrl,
      artifacts: report?.artifacts ?? null,
    });
  }

  async function downloadPdf() {
    setExporting(true);
    setExportError(null);
    try {
      const blob = await createReportPdfBlob({
        title,
        subtitle: meta,
        audience,
        summary: parsed.summary,
        sections: parsed.sections,
        facts,
        commits,
        artifacts: report?.artifacts ?? null,
        repositoryUrl,
      });
      downloadBlob(blob, `${fileBaseName}.pdf`);
    } catch (error) {
      setExportError(
        error instanceof Error ? error.message : "Не удалось подготовить PDF",
      );
    } finally {
      setExporting(false);
    }
  }

  function downloadMarkdown() {
    setExportError(null);
    downloadBlob(
      new Blob([markdown()], { type: "text/markdown;charset=utf-8" }),
      `${fileBaseName}.md`,
    );
  }

  async function copyMarkdown() {
    setExportError(null);
    try {
      await navigator.clipboard.writeText(markdown());
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2_000);
    } catch {
      setExportError("Браузер не разрешил доступ к буферу обмена. Скачайте Markdown-файл.");
    }
  }

  return (
    <motion.section
      animate={{ opacity: 1, y: 0 }}
      className="soft-panel overflow-clip"
      initial={reduceMotion ? false : { opacity: 0, y: 14 }}
      transition={{
        duration: reduceMotion ? 0 : 0.45,
        ease: [0.22, 1, 0.36, 1],
      }}
    >
      <header className="border-b border-line px-5 py-5 sm:px-8 sm:py-6">
        <div className="flex flex-col items-start justify-between gap-4 sm:flex-row sm:flex-wrap">
          <div className="flex min-w-0 flex-1 items-start gap-3">
            <span className="mt-0.5 flex size-10 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent">
              <FileText className="size-4" />
            </span>
            <div className="min-w-0">
              <h2 className="font-display text-lg font-semibold leading-snug text-ink sm:text-xl">
                {title}
              </h2>
              <p className="mt-1 text-xs text-muted">{meta}</p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {report.status === "PROCESSING" && (
              <FlowHoverButton
                className={stopButtonClasses}
                disabled={cancelling}
                icon={<Square className="size-3.5 fill-current" />}
                onClick={() => void onCancel()}
                type="button"
              >
                {cancelling ? "Останавливаем…" : "Остановить"}
              </FlowHoverButton>
            )}
            {report.status === "COMPLETED" && (
              <>
                <ExportMenu
                  busy={exporting}
                  copied={copied}
                  onCopyMarkdown={() => void copyMarkdown()}
                  onDownloadMarkdown={downloadMarkdown}
                  onDownloadPdf={() => void downloadPdf()}
                />
                <button
                  aria-label="Сформировать отчёт заново"
                  className="icon-button"
                  onClick={() => void onRetry()}
                  title="Сформировать отчёт заново"
                  type="button"
                >
                  <RefreshCw className="size-4" />
                </button>
              </>
            )}
            {(report.status === "FAILED" || report.status === "CANCELLED") && (
              <FlowHoverButton
                icon={<RotateCcw className="size-4" />}
                onClick={() => void onRetry()}
                type="button"
              >
                Повторить
              </FlowHoverButton>
            )}
          </div>
        </div>

        {inProgress && (
          <div className="mt-5">
            <div className="mb-2 flex items-center justify-between gap-3 text-xs text-muted">
              <span className="flex min-w-0 items-center gap-2">
                <LoaderCircle className="size-3.5 shrink-0 animate-spin text-accent motion-reduce:animate-none" />
                <span className="truncate">{stage}</span>
              </span>
              {!commitsAnalyzed && <span className="font-mono">{percentage}%</span>}
            </div>
            <div className="h-1.5 overflow-hidden rounded-full bg-accent-soft">
              <div
                className={`progress-fill h-full rounded-full bg-accent transition-[width] duration-500 motion-reduce:transition-none ${
                  commitsAnalyzed && report.status === "PROCESSING"
                    ? "animate-pulse motion-reduce:animate-none"
                    : ""
                }`}
                style={{ width: `${commitsAnalyzed ? 100 : percentage}%` }}
              />
            </div>
          </div>
        )}

        {report.status === "FAILED" && report.lastError && (
          <p className="mt-4 flex items-start gap-2 rounded-xl border border-red-300/60 bg-red-50/80 px-3 py-2 text-xs leading-5 text-red-700 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300">
            <AlertCircle className="mt-0.5 size-3.5 shrink-0" />
            <span className="break-words">{report.lastError}</span>
          </p>
        )}
        {exportError && (
          <p className="mt-4 rounded-xl border border-red-300/60 bg-red-50/80 px-3 py-2 text-xs text-red-700 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300">
            {exportError}
          </p>
        )}
      </header>

      <div className="grid gap-8 px-5 py-6 sm:px-8 sm:py-8 xl:grid-cols-[180px_minmax(0,1fr)]">
        <nav aria-label="Содержание отчёта" className="hidden xl:block">
          <div className="sticky top-6">
            <p className="eyebrow mb-3 flex items-center gap-1.5">
              <ListTree className="size-3.5" /> Содержание
            </p>
            <ol className="border-l border-line">
              {toc.map((item) => (
                <li key={item.id}>
                  <a className="report-toc-link" href={`#${item.id}`}>
                    {item.title}
                  </a>
                </li>
              ))}
            </ol>
          </div>
        </nav>

        <div className="min-w-0 space-y-10">
          {report.artifacts?.coverage && <ReviewCoverage artifacts={report.artifacts} />}
          {report.artifacts && !report.artifacts.coverage && <details className="rounded-xl border border-line bg-panel-strong p-4">
            <summary className="cursor-pointer text-sm font-semibold text-ink">Проверка источников: {report.artifacts.quality.validatedFindings} выводов и {report.artifacts.quality.validatedActions} задач</summary>
            <ul className="mt-3 space-y-1 text-xs text-muted">{report.artifacts.quality.checks.map(check => <li key={check}>✓ {check}</li>)}</ul>
            {report.artifacts.quality.warnings.map(warning => <p className="mt-3 text-xs leading-5 text-muted" key={warning}>{warning}</p>)}
          </details>}

          {parsed.summary.length > 0 && (
            <section
              className="report-anchor rounded-2xl border border-line bg-panel-strong p-5 sm:p-6"
              id="report-summary"
            >
              <p className="eyebrow">Главное</p>
              <ReportMarkdown
                className="report-summary mt-3"
                commits={commits}
                markdown={summaryMarkdown}
                repositoryUrl={repositoryUrl}
                onEvidence={setSelectedEvidence}
              />
            </section>
          )}

          {facts && <KeyNumbers facts={facts} layout={layout} />}

          {parsed.sections.length > 0
            ? parsed.sections.filter(section => !report.artifacts?.coverage || section.title !== "Покрытие проверки").map((section) => (
                <section className="report-anchor" id={section.id} key={section.id}>
                  <h3 className="report-section-title">{section.title}</h3>
                  {report.artifacts?.coverage
                    ? <GroundedFindings findings={report.artifacts.sections.find(item => item.title === section.title)?.findings ?? []} onEvidence={setSelectedEvidence} />
                    : <ReportMarkdown
                    className="max-w-3xl"
                    commits={commits}
                    markdown={section.body}
                    repositoryUrl={repositoryUrl}
                    onEvidence={setSelectedEvidence}
                  />}
                </section>
              ))
            : inProgress && (
                <div className="rounded-2xl border border-dashed border-line px-5 py-8 text-center text-sm leading-6 text-muted">
                  Выводы для роли «{role}» появятся после разбора всех коммитов.
                  <br />
                  Уже разобранные коммиты можно посмотреть ниже.
                </div>
              )}

          {facts && showFacts && <ReportFacts facts={facts} layout={layout} />}

          {report.artifacts && <>
            <ActionPlan key={`${report.id}:${report.updatedAt}`} actions={report.artifacts.actions} evidence={report.artifacts.evidence}
              reportId={report.id} repositoryUrl={repositoryUrl} onEvidence={setSelectedEvidence} />
            <EvidencePanel key={report.id} reportId={report.id} repositoryUrl={repositoryUrl}
              evidence={report.artifacts.evidence.filter(source => source.id === "F1" || source.id === "S1" ||
                [...report.artifacts!.summary, ...report.artifacts!.sections.flatMap(section => section.findings), ...report.artifacts!.actions]
                  .some(item => item.evidenceIds.includes(source.id)))}
              selectedId={selectedEvidence} onSelect={setSelectedEvidence} />
          </>}

          <CommitInsightList
            preliminary={!!report.artifacts?.coverage && report.artifacts.coverage.mode !== "SOURCES_ONLY"}
            commits={commits}
            defaultOpen={inProgress}
            repositoryUrl={repositoryUrl}
            totalCommits={report.totalCommits}
          />
        </div>
      </div>
    </motion.section>
  );
}

function periodSlug(report: AnalysisReport) {
  const { period } = report;
  if (period.type === "LAST_DAYS") return `${period.days}d`;
  if (period.type === "SINCE_TAG") return `since-${period.fromTag}`;
  return `${period.fromTag}-${period.toTag}`;
}
