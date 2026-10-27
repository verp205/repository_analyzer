import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useReportWorkspace } from "@/hooks/useReportWorkspace";
import { api } from "@/lib/api";
import type { AnalysisReport, ReportAudience, ReportPeriod } from "@/lib/types";
import { facts, insight, report, task } from "@/test/fixtures";

vi.mock("@/lib/api", () => ({ api: {
  getGraph: vi.fn(), getClusters: vi.fn(), getTags: vi.fn(), createReport: vi.fn(),
  listReports: vi.fn(), getReport: vi.fn(), getReportFacts: vi.fn(), getReportCommitInsights: vi.fn(),
  cancelReport: vi.fn(), retryReport: vi.fn(), reportEventsUrl: vi.fn((id: number) => `/events/${id}`),
} }));

class FakeEventSource {
  static CLOSED = 2;
  static instances: FakeEventSource[] = [];
  readyState = 1;
  onerror: (() => void) | null = null;
  listeners = new Map<string, (event: MessageEvent<string>) => void>();
  close = vi.fn(() => { this.readyState = FakeEventSource.CLOSED; });
  constructor(public url: string) { FakeEventSource.instances.push(this); }
  addEventListener(name: string, callback: (event: MessageEvent<string>) => void) { this.listeners.set(name, callback); }
  emit(name: string, update: object | string) {
    this.listeners.get(name)?.(new MessageEvent("message", { data: typeof update === "string" ? update : JSON.stringify(update) }));
  }
}
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { promise, resolve };
}
const flush = async () => { await act(async () => { await Promise.resolve(); await Promise.resolve(); }); };
const onError = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  FakeEventSource.instances = [];
  vi.stubGlobal("EventSource", FakeEventSource);
  vi.mocked(api.getGraph).mockResolvedValue({ analysisTaskId: 1, totalCommits: 0, rootCount: 0, tipCount: 0, mergeCommitCount: 0, maxDepth: 0, missingParentCount: 0, nodes: [], edges: [] });
  vi.mocked(api.getClusters).mockResolvedValue({ analysisTaskId: 1, clusteredCommits: 0, clusters: [] });
  vi.mocked(api.getTags).mockResolvedValue([]);
  vi.mocked(api.createReport).mockResolvedValue(report());
  vi.mocked(api.listReports).mockResolvedValue([]);
  vi.mocked(api.getReport).mockResolvedValue(report(10, "PROCESSING"));
  vi.mocked(api.getReportFacts).mockResolvedValue(facts);
  vi.mocked(api.getReportCommitInsights).mockResolvedValue([]);
});
afterEach(() => vi.useRealTimers());

describe("report workspace", () => {
  it("discards a late response after switching audience", async () => {
    const old = deferred<AnalysisReport>();
    vi.mocked(api.createReport).mockImplementation((_id, audience) => audience === "DEVELOPER" ? old.promise : Promise.resolve({ ...report(11), audience }));
    const { result, rerender } = renderHook(({ audience }) => useReportWorkspace(task, audience, { type: "ALL" }, onError),
      { initialProps: { audience: "DEVELOPER" as ReportAudience } });
    rerender({ audience: "SECURITY" });
    await waitFor(() => expect(result.current.report?.audience).toBe("SECURITY"));
    await act(async () => old.resolve(report(10)));
    expect(result.current.report?.id).toBe(11);
    expect(onError).not.toHaveBeenCalled();
    expect(api.getGraph).toHaveBeenCalledTimes(1);
    expect(api.getClusters).toHaveBeenCalledTimes(1);
    expect(api.getTags).toHaveBeenCalledTimes(1);
  });

  it("keeps facts and insights from the selected period when an earlier request finishes later", async () => {
    const oldFacts = deferred<typeof facts>();
    vi.mocked(api.createReport).mockImplementation((_id, _audience, selected) => Promise.resolve({ ...report(selected.type === "ALL" ? 10 : 11), period: { ...selected, label: "period" } }));
    vi.mocked(api.getReportFacts).mockImplementation(id => id === 10 ? oldFacts.promise : Promise.resolve({ ...facts, totalCommits: 1 }));
    const all: ReportPeriod = { type: "ALL" };
    const last: ReportPeriod = { type: "LAST_DAYS", days: 30 };
    const { result, rerender } = renderHook(({ period }) => useReportWorkspace(task, "DEVELOPER", period, onError), { initialProps: { period: all } });
    await waitFor(() => expect(result.current.report?.id).toBe(10));
    rerender({ period: last });
    await waitFor(() => expect(result.current.facts?.totalCommits).toBe(1));
    await act(async () => oldFacts.resolve(facts));
    expect(result.current.report?.id).toBe(11);
    expect(result.current.facts?.totalCommits).toBe(1);
  });

  it("reloads for a new analysis revision and also after an unchanged-head refresh", async () => {
    const period: ReportPeriod = { type: "ALL" };
    const { result, rerender } = renderHook(({ revision }) => useReportWorkspace({ ...task, revision }, "DEVELOPER", period, onError), { initialProps: { revision: 1 } });
    await waitFor(() => expect(result.current.report).not.toBeNull());
    vi.mocked(api.createReport).mockResolvedValue({ ...report(11), analysisRevision: 2 });
    rerender({ revision: 2 });
    await waitFor(() => expect(result.current.report?.id).toBe(11));
    await act(async () => result.current.resetWorkspace());
    await waitFor(() => expect(api.createReport).toHaveBeenCalledTimes(3));
    expect(api.getGraph).toHaveBeenCalledTimes(3);
    expect(api.getClusters).toHaveBeenCalledTimes(3);
  });

  it("opens a pinned historical report and returns to the current version after refresh", async () => {
    const latest = { ...report(11), analysisRevision: 2 };
    vi.mocked(api.createReport).mockResolvedValue(latest);
    vi.mocked(api.listReports).mockResolvedValue([report(10), latest]);
    vi.mocked(api.getReport).mockResolvedValue(report(10));
    const { result } = renderHook(() => useReportWorkspace({ ...task, revision: 2 }, "DEVELOPER", { type: "ALL" }, onError));
    await waitFor(() => expect(result.current.availableReports).toHaveLength(2));
    act(() => result.current.selectReport(10));
    await waitFor(() => expect(result.current.report?.analysisRevision).toBe(1));
    expect(api.getReportFacts).toHaveBeenLastCalledWith(10);
    act(() => result.current.resetWorkspace());
    await waitFor(() => expect(result.current.report?.id).toBe(11));
  });

  it("keeps newer streamed progress while a stale snapshot is loading, then fetches terminal content", async () => {
    vi.mocked(api.createReport).mockResolvedValue(report(10, "PROCESSING"));
    const oldSnapshot = deferred<AnalysisReport>();
    vi.mocked(api.getReport).mockReturnValueOnce(oldSnapshot.promise).mockResolvedValue(report(10));
    const { result } = renderHook(() => useReportWorkspace(task, "DEVELOPER", { type: "ALL" }, onError));
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    const stream = FakeEventSource.instances[0];
    act(() => stream.emit("snapshot", { reportId: 10, type: "SNAPSHOT", status: "PROCESSING", processedCommits: 0, totalCommits: 2 }));
    act(() => stream.emit("progress", { reportId: 10, type: "PROGRESS", processedCommits: 2, totalCommits: 2, commits: [insight(1), insight(2)] }));
    act(() => stream.emit("completed", { reportId: 10, type: "COMPLETED", status: "COMPLETED", processedCommits: 2, totalCommits: 2 }));
    await act(async () => oldSnapshot.resolve(report(10, "PROCESSING")));
    await waitFor(() => expect(result.current.report?.status).toBe("COMPLETED"));
    expect(result.current.commitInsights).toHaveLength(2);
    expect(api.getReport).toHaveBeenCalledTimes(2);
  });

  it("reloads missed insights on SSE reconnection and ignores events from another report", async () => {
    vi.mocked(api.createReport).mockResolvedValue(report(10, "PROCESSING"));
    const period: ReportPeriod = { type: "ALL" };
    const { result } = renderHook(() => useReportWorkspace(task, "DEVELOPER", period, onError));
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    const source = FakeEventSource.instances[0];
    source.readyState = 0;
    act(() => source.onerror?.()); // browser reconnects automatically
    vi.mocked(api.getReportCommitInsights).mockResolvedValue([insight(1), insight(2)]);
    act(() => source.emit("snapshot", { reportId: 10, type: "SNAPSHOT", status: "PROCESSING", processedCommits: 2, totalCommits: 2, commits: [] }));
    await waitFor(() => expect(result.current.commitInsights).toHaveLength(2));
    act(() => source.emit("progress", { reportId: 99, type: "PROGRESS", processedCommits: 9, totalCommits: 9, commits: [insight(3)] }));
    expect(result.current.commitInsights).toHaveLength(2);
    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it("polls after a permanently closed stream and stops after completion", async () => {
    vi.useFakeTimers();
    vi.mocked(api.createReport).mockResolvedValue(report(10, "PROCESSING"));
    const period: ReportPeriod = { type: "ALL" };
    const { result, unmount } = renderHook(() => useReportWorkspace(task, "DEVELOPER", period, onError));
    await flush();
    const source = FakeEventSource.instances[0];
    source.readyState = FakeEventSource.CLOSED;
    act(() => source.onerror?.());
    await flush();
    vi.mocked(api.getReport).mockResolvedValue(report(10));
    await act(async () => vi.advanceTimersByTimeAsync(4000));
    expect(result.current.report?.status).toBe("COMPLETED");
    const calls = vi.mocked(api.getReport).mock.calls.length;
    await act(async () => vi.advanceTimersByTimeAsync(12_000));
    expect(api.getReport).toHaveBeenCalledTimes(calls);
    expect(source.close).toHaveBeenCalled();
    unmount();
  });

  it("cancels a report and closes its stream", async () => {
    vi.mocked(api.createReport).mockResolvedValue(report(10, "PROCESSING"));
    vi.mocked(api.cancelReport).mockResolvedValue(report(10, "CANCELLED"));
    const period: ReportPeriod = { type: "ALL" };
    const { result } = renderHook(() => useReportWorkspace(task, "DEVELOPER", period, onError));
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    await act(async () => result.current.cancelReport());
    expect(api.cancelReport).toHaveBeenCalledWith(10);
    expect(result.current.report?.status).toBe("CANCELLED");
    expect(FakeEventSource.instances[0].close).toHaveBeenCalled();
  });

  it("reopens the stream when retrying a completed report", async () => {
    vi.mocked(api.retryReport).mockResolvedValue(report(10, "PROCESSING"));
    const period: ReportPeriod = { type: "ALL" };
    const { result } = renderHook(() => useReportWorkspace(task, "DEVELOPER", period, onError));
    await waitFor(() => expect(result.current.report?.status).toBe("COMPLETED"));
    await act(async () => result.current.retryReport());
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(result.current.report?.status).toBe("PROCESSING");
  });

  it("recovers from malformed SSE data instead of crashing", async () => {
    vi.mocked(api.createReport).mockResolvedValue(report(10, "PROCESSING"));
    const period: ReportPeriod = { type: "ALL" };
    const { result } = renderHook(() => useReportWorkspace(task, "DEVELOPER", period, onError));
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    vi.mocked(api.getReport).mockResolvedValue(report(10));
    act(() => FakeEventSource.instances[0].emit("progress", "{broken"));
    await waitFor(() => expect(result.current.report?.status).toBe("COMPLETED"));
    expect(onError).not.toHaveBeenCalled();
  });
});
