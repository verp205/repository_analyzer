import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "@/lib/api";
import { periodKey } from "@/lib/reportPeriod";
import type {
  AnalysisReport, AnalysisTask, CommitClusters, CommitGraph, CommitInsight,
  GitTag, ReportAudience, ReportEvent, ReportPeriod, ReportSummary, RepositoryFacts,
} from "@/lib/types";

interface Workspace {
  key: string;
  report: AnalysisReport | null;
  graph: CommitGraph | null;
  clusters: CommitClusters | null;
  facts: RepositoryFacts | null;
  commits: CommitInsight[];
  tags: GitTag[];
  reports: ReportSummary[];
}

const empty: Workspace = { key: "", report: null, graph: null, clusters: null, facts: null, commits: [], tags: [], reports: [] };
const eventNames = ["snapshot", "started", "progress", "synthesis", "completed", "failed", "cancelled"];
const isTerminal = (status: string) => ["COMPLETED", "FAILED", "CANCELLED"].includes(status);
const message = (error: unknown) => error instanceof Error ? error.message : "Не удалось загрузить отчёт";

export function useReportWorkspace(task: AnalysisTask | null, audience: ReportAudience, period: ReportPeriod,
                                   onError: (error: string) => void) {
  const { type, days, fromTag, toTag } = period;
  const requestedPeriod = useMemo<ReportPeriod>(() => ({ type, days, fromTag, toTag }), [type, days, fromTag, toTag]);
  const taskId = task?.id;
  const revision = task?.revision ?? 0;
  const ready = task?.status === "COMPLETED";
  const baseKey = ready ? `${taskId}:${revision}:${audience}:${periodKey(requestedPeriod)}` : "";
  const [selection, setSelection] = useState<{ key: string; id: number } | null>(null);
  const selectedId = selection?.key === baseKey ? selection.id : null;
  const key = baseKey ? `${baseKey}:${selectedId ?? "latest"}` : "";
  const [reloadVersion, setReloadVersion] = useState(0);
  const [state, setState] = useState<Workspace>(empty);
  const [cancellingReport, setCancellingReport] = useState(false);
  const metadata = useRef<{ key: string; promise: Promise<[CommitGraph, CommitClusters, GitTag[]]> } | null>(null);
  const current = key && state.key === key ? state : empty;
  const reportId = current.report?.id;
  const terminal = current.report ? isTerminal(current.report.status) : true;

  useEffect(() => {
    if (!ready || taskId == null) return;
    let disposed = false;
    const load = async () => {
      try {
        const metadataKey = `${taskId}:${revision}:${reloadVersion}`;
        if (metadata.current?.key !== metadataKey) {
          const promise: Promise<[CommitGraph, CommitClusters, GitTag[]]> = Promise.all([
            api.getGraph(taskId), api.getClusters(taskId), api.getTags(taskId).catch(() => [] as GitTag[]),
          ]);
          metadata.current = { key: metadataKey, promise };
          void promise.catch(() => { if (metadata.current?.promise === promise) metadata.current = null; });
        }
        const [[graph, clusters, tags], report] = await Promise.all([
          metadata.current.promise,
          selectedId == null ? api.createReport(taskId, audience, requestedPeriod) : api.getReport(selectedId),
        ]);
        if (disposed) return;
        setState({ ...empty, key, graph, clusters, report, tags });
        const [facts, commits, reports] = await Promise.all([api.getReportFacts(report.id), api.getReportCommitInsights(report.id),
          api.listReports(taskId).catch(() => [] as ReportSummary[])]);
        if (!disposed) setState(value => value.key === key && value.report?.id === report.id ? { ...value, facts, reports, commits: mergeInsights(value.commits, commits) } : value);
      } catch (error) {
        if (!disposed) onError(message(error));
      }
    };
    void load();
    return () => { disposed = true; };
  }, [key, ready, taskId, revision, audience, requestedPeriod, onError, reloadVersion, selectedId]);

  useEffect(() => {
    if (reportId == null || terminal) return;
    let disposed = false;
    let loading = false;
    let refreshAgain = false;
    let fallback: ReturnType<typeof setInterval> | undefined;
    const source = new EventSource(api.reportEventsUrl(reportId));
    const refresh = async () => {
      if (disposed) return;
      if (loading) { refreshAgain = true; return; }
      loading = true;
      try {
        const [report, facts, commits] = await Promise.all([
          api.getReport(reportId), api.getReportFacts(reportId), api.getReportCommitInsights(reportId),
        ]);
        if (!disposed) setState(value => value.key === key && value.report?.id === reportId ? { ...value, report: mergeReport(value.report, report), facts, commits: mergeInsights(value.commits, commits) } : value);
      } catch (error) {
        if (!disposed) onError(message(error));
      } finally {
        loading = false;
        if (refreshAgain && !disposed) { refreshAgain = false; void refresh(); }
      }
    };
    const handleEvent = (event: MessageEvent<string>) => {
      if (disposed) return;
      let update: ReportEvent;
      try { update = JSON.parse(event.data) as ReportEvent; }
      catch { void refresh(); return; }
      if (update.reportId !== reportId) return;
      setState(value => value.key === key && value.report?.id === reportId ? {
        ...value,
        report: { ...value.report, processedCommits: Math.max(value.report.processedCommits, update.processedCommits), totalCommits: update.totalCommits },
        commits: mergeInsights(value.commits, update.commits ?? []),
      } : value);
      if (update.type === "SNAPSHOT" || (update.status && isTerminal(update.status))) void refresh();
    };
    eventNames.forEach(name => source.addEventListener(name, handleEvent));
    source.onerror = () => {
      if (!disposed && source.readyState === EventSource.CLOSED && !fallback) {
        void refresh();
        fallback = setInterval(() => void refresh(), 4000);
      }
    };
    return () => {
      disposed = true;
      if (fallback) clearInterval(fallback);
      source.close();
    };
  }, [key, reportId, terminal, onError]);

  const resetWorkspace = useCallback(() => { setSelection(null); setState(empty); setReloadVersion(value => value + 1); }, []);
  const retryReport = async () => {
    if (reportId == null) return;
    try {
      const report = await api.retryReport(reportId);
      setState(value => value.key === key && value.report?.id === reportId ? { ...value, report } : value);
    } catch (error) { onError(message(error)); }
  };
  const cancelReport = async () => {
    if (reportId == null || current.report?.status !== "PROCESSING") return;
    setCancellingReport(true);
    try {
      const report = await api.cancelReport(reportId);
      setState(value => value.key === key && value.report?.id === reportId ? { ...value, report } : value);
    } catch (error) { onError(message(error)); }
    finally { setCancellingReport(false); }
  };
  const selectReport = (id: number) => setSelection({ key: baseKey, id });
  const availableReports = current.reports.filter(item => item.audience === audience && periodKey(item.period) === periodKey(requestedPeriod))
    .sort((a, b) => b.analysisRevision - a.analysisRevision || b.id - a.id);
  return { ...current, availableReports, selectReport, commitInsights: current.commits, cancellingReport, resetWorkspace, retryReport, cancelReport };
}

function mergeInsights(current: CommitInsight[], incoming: CommitInsight[]) {
  const byCommit = new Map(current.map(item => [item.commitId, item]));
  incoming.forEach(item => byCommit.set(item.commitId, item));
  return [...byCommit.values()].sort((a, b) => a.position - b.position);
}

function mergeReport(current: AnalysisReport, incoming: AnalysisReport): AnalysisReport {
  if (isTerminal(incoming.status)) return incoming;
  return { ...incoming, status: current.status === "PROCESSING" ? "PROCESSING" : incoming.status,
    processedCommits: Math.max(current.processedCommits, incoming.processedCommits) };
}
