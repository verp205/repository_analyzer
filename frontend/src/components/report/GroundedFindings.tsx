import type { ReportArtifacts, ReportFinding } from "@/lib/types";
import { riskLabel } from "@/lib/labels";

export function ReviewCoverage({ artifacts }: { artifacts: ReportArtifacts }) {
  const coverage = artifacts.coverage;
  if (!coverage) return null;
  const sourcesOnly = coverage.mode === "SOURCES_ONLY";
  return <section id="report-coverage" className="report-anchor rounded-xl border border-line bg-panel-strong p-4" aria-label="Покрытие проверки">
    <h3 className="text-sm font-semibold text-ink">{sourcesOnly ? "Факты и источники" : coverage.unreviewedCommits || coverage.limitedFiles ? "Проверка с ограничениями" : "Повторный разбор по коду"}</h3>
    <p className="mt-2 text-sm text-ink">{!sourcesOnly && <>Коммиты: {coverage.reviewedCommits} из {coverage.totalCommits} · </>}Фрагменты diff файлов: {coverage.inspectedFiles} из {coverage.changedFiles}</p>
    <p className="mt-1 text-xs leading-5 text-muted">{!sourcesOnly && <>Коммитов без повторного вывода: {coverage.unreviewedCommits}. </>}Файлов с ограничениями: {coverage.limitedFiles}. Один файл в разных коммитах учитывается отдельно.</p>
    <p className="mt-2 text-xs leading-5 text-muted">Числа рассчитаны программой. Выводы и риски — оценки модели. {sourcesOnly && "Независимая смысловая проверка не выполнялась. "}Тесты не запускались.</p>
    <details className="mt-3 text-xs text-muted"><summary className="cursor-pointer">Границы проверки</summary>
      <ul className="mt-2 space-y-1">{coverage.limitations.map(value => <li key={value}>{value}</li>)}</ul>
      <p className="mt-2">Файлов с контекстом: {coverage.contextFiles}. Отклонено вариантов разбора: {coverage.rejectedResponses}.</p>
    </details>
  </section>;
}

export function GroundedFindings({ findings, onEvidence }: { findings: ReportFinding[]; onEvidence: (id: string) => void }) {
  return <div className="space-y-3">{findings.map((finding, index) => {
    const assessment = finding.assessment;
    return <article key={index} className="min-w-0 rounded-xl border border-line p-4">
      <div className="flex flex-wrap items-center gap-2 text-[11px] font-semibold text-muted">
        <span>{assessment?.kind === "FACT" ? "Рассчитанный факт" : assessment?.kind === "UNKNOWN" ? "Не установлено" : "Оценка модели"}</span>
        {assessment?.kind === "INFERENCE" && assessment.riskLevel && <span className={assessment.riskLevel === "HIGH" ? "text-red-700 dark:text-red-300" : ""}>
          {assessment.riskLevel === "NONE" ? "Основание для риска не найдено" : `${riskLabel(assessment.riskLevel)} риск`}
        </span>}
      </div>
      <h4 className="mt-2 break-words text-sm font-semibold leading-6 text-ink">{finding.text}</h4>
      {assessment?.impact && <p className="mt-2 text-sm leading-6 text-ink"><span className="font-medium">Последствие: </span>{assessment.impact}</p>}
      {assessment?.risk && <p className="mt-2 text-sm leading-6 text-ink"><span className="font-medium">Основание риска: </span>{assessment.risk}</p>}
      {!!assessment?.unknowns.length && <p className="mt-2 text-xs leading-5 text-muted">Не установлено: {assessment.unknowns.join(" ")}</p>}
      {!!assessment?.citations.length && <details className="mt-3">
        <summary className="cursor-pointer text-xs font-semibold text-accent">Код и источники · {assessment.citations.length}</summary>
        <div className="mt-3 space-y-3">{assessment.citations.map(citation => <div key={`${citation.commitHash}:${citation.path}:${citation.side}:${citation.id}`} className="min-w-0">
          <button type="button" onClick={() => onEvidence(citation.evidenceId)} className="break-all text-left text-xs text-accent">
            {citation.path} · {citation.commitHash.slice(0, 7)} · {({ DIFF: "diff", BEFORE: "до", AFTER: "после", RELATED: "связанный фрагмент" })[citation.side]} · {citation.startLine}–{citation.endLine}
          </button>
          <pre className="mt-2 max-h-72 overflow-auto rounded-lg bg-panel-strong p-3 text-[11px] leading-5 text-muted" aria-label={`Цитата ${citation.path}`}><code>{citation.code}</code></pre>
        </div>)}</div>
      </details>}
      {!assessment?.citations.length && <div className="mt-3 flex flex-wrap gap-2">{finding.evidenceIds.map(id => <button key={id} type="button" className="text-xs text-accent" onClick={() => onEvidence(id)}>Источник {id}</button>)}</div>}
    </article>;
  })}</div>;
}
