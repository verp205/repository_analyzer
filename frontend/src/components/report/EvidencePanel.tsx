import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import type { EvidenceDetail, EvidenceFile, ReportEvidence } from "@/lib/types";
import { describeEvidence } from "@/lib/reportEvidence";
import { commitUrl } from "@/lib/utils";

interface Props {
  reportId: number;
  repositoryUrl: string;
  evidence: ReportEvidence[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export function EvidencePanel({ reportId, repositoryUrl, evidence, selectedId, onSelect }: Props) {
  const source = evidence.find(item => item.id === selectedId);
  useEffect(() => {
    if (selectedId) document.getElementById("report-evidence")?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }, [selectedId]);
  return (
    <section className="report-anchor" id="report-evidence" aria-label="Источники отчёта">
      <h3 className="report-section-title">Источники отчёта</h3>
      <p className="mb-4 text-xs leading-5 text-muted">Нажмите ссылку рядом с выводом, чтобы проверить исходные данные. Diff показывает изменения и номера строк в заголовках @@.</p>
      <div className="flex max-h-40 flex-wrap gap-2 overflow-y-auto">
        {evidence.map(item => <button className={`rounded-md border px-2 py-1 text-xs ${selectedId === item.id ? "border-accent bg-accent-soft text-accent" : "border-line text-muted"}`}
          key={item.id} onClick={() => onSelect(item.id)} title={item.label} type="button">{item.id}{item.commitHash && item.kind === "COMMIT" ? ` · ${item.commitHash.slice(0, 7)}` : ""} · {item.label.length > 60 ? `${item.label.slice(0, 60)}…` : item.label}</button>)}
      </div>
      {source && <EvidenceSourceDetails key={`${reportId}:${source.id}`} source={source} reportId={reportId} repositoryUrl={repositoryUrl} />}

    </section>
  );
}

function EvidenceSourceDetails({ source, reportId, repositoryUrl }: { source: ReportEvidence; reportId: number; repositoryUrl: string }) {
  const [activeDetail, setDetail] = useState<EvidenceDetail | null>(null);
  const [activePatch, setPatch] = useState<EvidenceFile | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loadingFile, setLoadingFile] = useState(false);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    if (source.kind !== "FACTS") void api.getEvidence(reportId, source.id).then(value => {
      if (active.current) setDetail(value);
    }).catch(caught => { if (active.current) setError(caught instanceof Error ? caught.message : "Не удалось загрузить источник"); });
    return () => { active.current = false; };
  }, [reportId, source.id, source.kind]);
  const openFile = (position: number) => {
    setLoadingFile(true);
    setError(null);
    void api.getEvidenceFile(reportId, source.id, position)
      .then(value => { if (active.current) setPatch(value); })
      .catch(caught => { if (active.current) setError(caught instanceof Error ? caught.message : "Не удалось загрузить файл"); })
      .finally(() => { if (active.current) setLoadingFile(false); });
  };
  return (
    <div className="mt-4 rounded-xl border border-line bg-panel-strong p-4">
        <h4 className="break-words text-sm font-semibold text-ink">{source.id} · {source.label}</h4>
        {source.commitHash && <p className="mt-2 break-all font-mono text-xs text-muted">{source.commitHash}</p>}
        {source.kind !== "FACTS" ? <>
          {source.kind === "COMMIT" ? <p className="mt-2 text-xs text-muted">{source.description}</p> :
            <dl className="mt-3 grid gap-2 text-xs">{describeEvidence(source).map(([label, value]) => <div className="grid gap-1 sm:grid-cols-[180px_1fr]" key={label}><dt className="text-muted">{label}</dt><dd className="break-words text-ink">{value}</dd></div>)}</dl>}
          {source.commitHash && commitUrl(repositoryUrl, source.commitHash) && <a className="mt-3 inline-block text-xs text-accent" href={commitUrl(repositoryUrl, source.commitHash)!} target="_blank" rel="noreferrer">Открыть коммит</a>}
          {!activeDetail && !error && <p className="mt-3 text-xs text-muted">Загружаем файлы…</p>}
          <ul className="mt-4 space-y-2">{activeDetail?.files.map((file, position) => <li key={position}>
            <button className="flex w-full items-start justify-between gap-2 rounded-lg border border-line px-3 py-2 text-left text-xs text-ink disabled:opacity-50"
              disabled={loadingFile} type="button" onClick={() => openFile(position)}>
              <span className="break-all font-mono">{file.oldPath && file.oldPath !== file.newPath ? `${file.oldPath} → ` : ""}{file.newPath ?? "удалён"}</span>
              <span className="shrink-0 text-muted">{file.changeType === "SNAPSHOT" ? "Открыть файл" : `${file.changeType} · +${file.addedLines} / −${file.deletedLines}`}</span>
            </button>
          </li>)}</ul>
          {activePatch && <div className="mt-4">
            <p className="mb-2 break-all text-xs text-muted">{activePatch.newPath ?? activePatch.oldPath}</p>
            <pre className="max-h-[32rem] overflow-auto rounded-lg bg-panel p-3 font-mono text-[11px] leading-5" aria-label="Diff источника">
              {activePatch.patch ? activePatch.patch.split("\n").map((line, index) => <span className={`block ${line.startsWith("+") ? "text-green-700 dark:text-green-300" : line.startsWith("-") ? "text-red-700 dark:text-red-300" : line.startsWith("@@") ? "text-accent" : "text-muted"}`} key={index}>{line || " "}</span>) : "Текстовый diff отсутствует (пустое изменение или бинарный файл)."}
            </pre>
            {activePatch.truncated && <p className="mt-2 text-xs text-muted">Показаны первые 24 000 символов. Полный diff доступен в коммите.</p>}
          </div>}
        </> : <dl className="mt-3 grid gap-2 text-xs">{describeEvidence(source).map(([label, value]) => <div className="grid gap-1 sm:grid-cols-[180px_1fr]" key={label}><dt className="text-muted">{label}</dt><dd className="break-words text-ink">{value}</dd></div>)}</dl>}
        {error && <p role="alert" className="mt-3 text-xs text-red-600">{error}</p>}
      </div>
  );
}

