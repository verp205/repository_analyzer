import { useState } from "react";
import { buildActionPlanMarkdown, actionPlanJson, priorityLabel } from "@/lib/actionPlan";
import { downloadBlob } from "@/lib/download";
import type { ReportAction, ReportEvidence } from "@/lib/types";

interface Props {
  actions: ReportAction[];
  evidence: ReportEvidence[];
  reportId: number;
  repositoryUrl: string;
  onEvidence: (id: string) => void;
}

export function ActionPlan({ actions, evidence, reportId, repositoryUrl, onEvidence }: Props) {
  const [selected, setSelected] = useState(() => new Set(actions.map(action => action.id)));
  const chosen = actions.filter(action => selected.has(action.id));
  function download(format: "md" | "json") {
    const content = format === "md" ? buildActionPlanMarkdown(chosen, evidence, repositoryUrl) : actionPlanJson(chosen, evidence, reportId);
    downloadBlob(new Blob([content], { type: format === "md" ? "text/markdown;charset=utf-8" : "application/json;charset=utf-8" }), `action-plan-${reportId}.${format}`);
  }
  return (
    <section className="report-anchor" id="report-actions" aria-label="План действий">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="report-section-title">План действий</h3>
        {actions.length > 0 && <div className="flex gap-2">
          <button className="rounded-lg border border-line px-3 py-2 text-xs text-ink disabled:opacity-40" disabled={!chosen.length} onClick={() => download("md")} type="button">Выбранные в Markdown ({chosen.length})</button>
          <button className="rounded-lg border border-line px-3 py-2 text-xs text-ink disabled:opacity-40" disabled={!chosen.length} onClick={() => download("json")} type="button">JSON</button>
        </div>}
      </div>
      {actions.length === 0 ? <p className="text-sm text-muted">По этим данным конкретных задач не сформировано.</p> : (
        <ol className="mt-4 space-y-3">
          {actions.map(action => <li className="rounded-xl border border-line p-4" key={action.id}>
            <label className="flex cursor-pointer items-start gap-3">
              <input type="checkbox" className="mt-1 accent-[var(--color-accent)]" checked={selected.has(action.id)}
                onChange={() => setSelected(current => { const next = new Set(current); if (next.has(action.id)) next.delete(action.id); else next.add(action.id); return next; })} />
              <span className="flex-1 text-sm font-semibold text-ink">{action.title}</span>
              <span className={`rounded-md px-2 py-1 text-[10px] ${action.priority === "HIGH" ? "bg-red-500/10 text-red-600" : action.priority === "MEDIUM" ? "bg-amber-500/10 text-amber-700" : "bg-accent-soft text-accent"}`}>{priorityLabel(action.priority)}</span>
            </label>
            <p className="mt-3 text-sm leading-6 text-muted">{action.reason}</p>
            <p className="mt-2 break-all text-xs text-muted">{action.paths.map(path => <code className="mr-2" key={path}>{path}</code>)}</p>
            <p className="mt-3 text-sm leading-6 text-ink"><span className="font-semibold">Проверка результата: </span>{action.verification}</p>
            <div className="mt-3 flex flex-wrap gap-2">{action.evidenceIds.map(id => <button className="rounded-md bg-accent-soft px-2 py-1 text-xs text-accent" key={id} onClick={() => onEvidence(id)} type="button">Источник {id}</button>)}</div>
          </li>)}
        </ol>
      )}
    </section>
  );
}
