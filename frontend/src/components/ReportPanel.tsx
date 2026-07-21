import { Download, FileText, LoaderCircle, RotateCcw } from "lucide-react";
import ReactMarkdown from "react-markdown";
import { useEffect, useState } from "react";
import {
  FlowHoverButton,
  FlowHoverLink,
} from "@/components/ui/flow-hover-button";
import { audienceLabel } from "@/lib/labels";
import { createReportPdfBlob } from "@/lib/pdf";
import type { AnalysisReport } from "@/lib/types";

interface ReportPanelProps {
  report: AnalysisReport | null;
  liveContent: string;
  onRetry: () => Promise<void>;
}

export function ReportPanel({ report, liveContent, onRetry }: ReportPanelProps) {
  const [pdfAsset, setPdfAsset] = useState<{
    key: string;
    url: string;
  } | null>(null);
  const [pdfFailure, setPdfFailure] = useState<{
    key: string;
    message: string;
  } | null>(null);
  const content = report?.content || liveContent;
  const pdfKey =
    report &&
    content &&
    report.status !== "NEW" &&
    report.status !== "PROCESSING"
      ? `${report.id}:${report.audience}:${report.updatedAt}`
      : null;
  const pdfUrl = pdfAsset?.key === pdfKey ? pdfAsset.url : null;
  const exportError = pdfFailure?.key === pdfKey ? pdfFailure.message : null;
  const preparingPdf = Boolean(pdfKey && !pdfUrl && !exportError);

  useEffect(() => {
    if (!report || !content || !pdfKey) return;

    let disposed = false;
    let generatedUrl = "";

    void createReportPdfBlob({
      audience: audienceLabel(report.audience),
      content,
      taskId: report.analysisTaskId,
    })
      .then((blob) => {
        if (disposed) return;
        generatedUrl = URL.createObjectURL(blob);
        setPdfAsset({ key: pdfKey, url: generatedUrl });
      })
      .catch((error: unknown) => {
        if (disposed) return;
        setPdfFailure({
          key: pdfKey,
          message:
            error instanceof Error
              ? error.message
              : "Не удалось подготовить PDF",
        });
      });

    return () => {
      disposed = true;
      if (generatedUrl) URL.revokeObjectURL(generatedUrl);
    };
  }, [content, pdfKey, report]);

  if (!report) return null;
  const percentage = report.totalCommits
    ? Math.round((report.processedCommits / report.totalCommits) * 100)
    : 0;

  const pdfFileName = `repository-report-${report.analysisTaskId}-${report.audience.toLowerCase()}.pdf`;

  return (
    <section className="soft-panel overflow-hidden">
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-line px-5 py-4 sm:px-7">
        <div className="flex items-center gap-3">
          <span className="flex size-9 items-center justify-center rounded-xl bg-accent-soft text-accent">
            <FileText className="size-4" />
          </span>
          <div>
            <p className="text-sm font-semibold text-ink">
              Отчёт для роли «{audienceLabel(report.audience)}»
            </p>
            <p className="text-xs text-muted">
              {report.status === "COMPLETED"
                ? "Формирование завершено"
                : report.status === "FAILED"
                  ? "Формирование остановлено"
                  : `Формируется в реальном времени · ${percentage}%`}
            </p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {report.status === "PROCESSING" && (
            <LoaderCircle className="size-5 animate-spin text-accent" />
          )}
          {content && pdfUrl && (
            <FlowHoverLink
              download={pdfFileName}
              href={pdfUrl}
              icon={<Download className="size-4" />}
            >
              Скачать PDF
            </FlowHoverLink>
          )}
          {content && !pdfUrl && (
            <FlowHoverButton
              disabled
              icon={<LoaderCircle className="size-4 animate-spin" />}
              type="button"
            >
              {preparingPdf
                ? "Подготавливаем PDF…"
                : pdfKey
                  ? "PDF недоступен"
                  : "PDF после завершения"}
            </FlowHoverButton>
          )}
          {report.status === "FAILED" && (
            <FlowHoverButton
              icon={<RotateCcw className="size-4" />}
              onClick={() => void onRetry()}
              type="button"
            >
              Повторить формирование
            </FlowHoverButton>
          )}
        </div>
      </header>
      <div className="max-h-[720px] overflow-y-auto px-5 py-6 sm:px-8 sm:py-8">
        {exportError && (
          <p className="mb-4 rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700">
            {exportError}
          </p>
        )}
        {content ? (
          <article className="report-markdown">
            <ReactMarkdown>{content}</ReactMarkdown>
          </article>
        ) : (
          <div className="flex min-h-44 items-center justify-center text-sm text-muted">
            Первый раздел отчёта появится здесь.
          </div>
        )}
      </div>
    </section>
  );
}
