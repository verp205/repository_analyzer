import { ChevronDown, ExternalLink, Search } from "lucide-react";
import { type ReactNode, useMemo, useState } from "react";
import { RiskBadge } from "@/components/report/RiskBadge";
import type { CommitInsight, RiskLevel } from "@/lib/types";
import { cn, commitUrl, formatDate, formatNumber } from "@/lib/utils";

type RiskFilter = "ALL" | "ATTENTION" | "LOW" | "NONE";

const PAGE_SIZE = 40;

const filters: { id: RiskFilter; label: string }[] = [
  { id: "ALL", label: "Все" },
  { id: "ATTENTION", label: "Требуют внимания" },
  { id: "LOW", label: "Низкий риск" },
  { id: "NONE", label: "Без риска" },
];

interface CommitInsightListProps {
  preliminary?: boolean;
  commits: CommitInsight[];
  repositoryUrl: string;
  totalCommits: number;
  defaultOpen: boolean;
}

export function CommitInsightList({
  commits,
  repositoryUrl,
  totalCommits,
  defaultOpen,
  preliminary = false,
}: CommitInsightListProps) {
  const [open, setOpen] = useState(defaultOpen);
  const [filter, setFilter] = useState<RiskFilter>("ALL");
  const [query, setQuery] = useState("");
  const [limit, setLimit] = useState(PAGE_SIZE);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const counts = useMemo(() => {
    const result: Record<RiskFilter, number> = {
      ALL: commits.length,
      ATTENTION: 0,
      LOW: 0,
      NONE: 0,
    };
    for (const commit of commits) result[riskGroup(commit.riskLevel)]++;
    return result;
  }, [commits]);

  const visible = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return commits
      .filter((commit) => filter === "ALL" || riskGroup(commit.riskLevel) === filter)
      .filter(
        (commit) =>
          !normalized ||
          [
            commit.commitHash,
            commit.summary,
            commit.message ?? "",
            commit.authorName ?? "",
            commit.categoryName,
            commit.details.pullRequest?.title ?? "",
          ].some((value) => value.toLowerCase().includes(normalized)),
      )
      .sort((left, right) => right.position - left.position);
  }, [commits, filter, query]);

  return (
    <section className="report-anchor" id="report-commits">
      <h3 className="report-section-title">
        <button
          aria-controls="report-commit-list"
          aria-expanded={open}
          className="flex w-full items-center justify-between gap-4 text-left"
          onClick={() => setOpen((current) => !current)}
          type="button"
        >
          <span>{preliminary ? "Первичный разбор коммитов" : "Коммиты"}</span>
          <ChevronDown
            className={cn("size-4 text-muted transition-transform", open && "rotate-180")}
          />
        </button>
      </h3>
      <p className="-mt-1 text-xs text-muted">
        Разобрано {formatNumber(commits.length)} из {formatNumber(totalCommits)}
        {counts.ATTENTION > 0 && ` · требуют внимания: ${formatNumber(counts.ATTENTION)}`}
      </p>

      {preliminary && <p className="mt-2 text-xs leading-5 text-muted">Здесь сохранены первоначальные оценки модели. Повторно проверенные выводы и уровни риска приведены выше.</p>}
      {open && (
        <div className="mt-4" id="report-commit-list">
          <div className="flex flex-wrap items-center gap-2">
            {filters.map((item) => (
              <button
                aria-pressed={filter === item.id}
                className={cn(
                  "rounded-full border px-3 py-1 text-xs transition-colors",
                  filter === item.id
                    ? "border-accent bg-accent text-accent-contrast"
                    : "border-line bg-panel text-muted hover:text-ink",
                )}
                key={item.id}
                onClick={() => {
                  setFilter(item.id);
                  setLimit(PAGE_SIZE);
                }}
                type="button"
              >
                {item.label} <span className="opacity-70">{counts[item.id]}</span>
              </button>
            ))}
            <label className="relative w-full sm:ml-auto sm:w-60">
              <span className="sr-only">Поиск по коммитам</span>
              <Search className="pointer-events-none absolute left-3 top-1/2 size-3.5 -translate-y-1/2 text-muted" />
              <input
                className="w-full rounded-full border border-line bg-panel py-1.5 pl-8 pr-3 text-xs text-ink outline-none placeholder:text-muted focus:border-accent"
                onChange={(event) => {
                  setQuery(event.target.value);
                  setLimit(PAGE_SIZE);
                }}
                placeholder="Хэш, автор или текст"
                type="search"
                value={query}
              />
            </label>
          </div>

          {visible.length === 0 ? (
            <p className="mt-6 text-center text-sm text-muted">
              {commits.length === 0
                ? "Разобранные коммиты появятся здесь."
                : "Ничего не найдено."}
            </p>
          ) : (
            <ul className="mt-4 space-y-2">
              {visible.slice(0, limit).map((commit) => (
                <CommitRow
                  commit={commit}
                  expanded={expandedId === commit.commitId}
                  key={commit.commitId}
                  onToggle={() =>
                    setExpandedId((current) =>
                      current === commit.commitId ? null : commit.commitId,
                    )
                  }
                  repositoryUrl={repositoryUrl}
                />
              ))}
            </ul>
          )}

          {visible.length > limit && (
            <button
              className="mt-3 w-full rounded-xl border border-dashed border-line py-2 text-xs text-muted transition-colors hover:text-ink"
              onClick={() => setLimit((current) => current + PAGE_SIZE)}
              type="button"
            >
              Показать ещё {Math.min(PAGE_SIZE, visible.length - limit)}
            </button>
          )}
        </div>
      )}
    </section>
  );
}

function CommitRow({
  commit,
  expanded,
  onToggle,
  repositoryUrl,
}: {
  commit: CommitInsight;
  expanded: boolean;
  onToggle: () => void;
  repositoryUrl: string;
}) {
  const url = commitUrl(repositoryUrl, commit.commitHash);
  const details = commit.details;
  const message = commit.message?.split("\n")[0]?.trim();

  return (
    <li className="rounded-xl border border-line bg-panel">
      <button
        aria-expanded={expanded}
        className="flex w-full items-start gap-3 px-3.5 py-3 text-left sm:px-4"
        onClick={onToggle}
        type="button"
      >
        <RiskBadge className="mt-0.5" risk={commit.riskLevel} />
        <span className="min-w-0 flex-1">
          <span className={cn("block text-sm leading-5 text-ink", !expanded && "line-clamp-2")}>
            {commit.summary}
          </span>
          <span className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-[11px] text-muted">
            <span className="font-mono">{commit.commitHash.slice(0, 7)}</span>
            <span>{commit.categoryName}</span>
            {details.pullRequest && <span>PR #{details.pullRequest.number}</span>}
            <span>{formatDate(commit.committedAt)}</span>
            {commit.authorName && <span>{commit.authorName}</span>}
            <span className="font-mono">
              +{formatNumber(commit.addedLines)} −{formatNumber(commit.deletedLines)}
            </span>
          </span>
        </span>
        <ChevronDown
          className={cn(
            "mt-1 size-4 shrink-0 text-muted transition-transform",
            expanded && "rotate-180",
          )}
        />
      </button>

      {expanded && (
        <div className="space-y-3 border-t border-line px-3.5 py-3 text-xs leading-5 text-muted sm:px-4">
          {message && (
            <Detail title="Сообщение коммита">
              <p>{message}</p>
            </Detail>
          )}
          {details.pullRequest && (
            <Detail title="Pull request">
              <p>
                {details.pullRequest.url ? (
                  <a
                    className="font-medium text-ink underline-offset-2 hover:underline"
                    href={details.pullRequest.url}
                    rel="noreferrer"
                    target="_blank"
                  >
                    #{details.pullRequest.number} {details.pullRequest.title}
                  </a>
                ) : (
                  `#${details.pullRequest.number} ${details.pullRequest.title}`
                )}
              </p>
              {details.pullRequest.issueTitles.length > 0 && (
                <p className="mt-0.5">
                  Задачи: {details.pullRequest.issueTitles.join("; ")}
                </p>
              )}
            </Detail>
          )}
          <DetailList items={details.keyChanges} title="Ключевые изменения" />
          {details.behaviorImpact && (
            <Detail title="Влияние на поведение">
              <p>{details.behaviorImpact}</p>
            </Detail>
          )}
          <DetailList items={details.risks} title="Риски" />
          <DetailList items={details.securityNotes} title="Безопасность" />
          {details.testNotes && (
            <Detail title="Тесты">
              <p>{details.testNotes}</p>
            </Detail>
          )}
          <DetailList items={details.checks} title="Что проверить" />

          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 pt-1 text-[11px]">
            <span>Файлов: {formatNumber(commit.changedFiles)}</span>
            {commit.status === "TEMPLATE" && <span>Описан системой без нейросети</span>}
            {commit.status === "UNSTRUCTURED" && (
              <span>Модель вернула ответ не в структурированном виде</span>
            )}
            {commit.status === "FAILED" && (
              <span className="text-red-600 dark:text-red-300">
                Будет проанализирован повторно при пересоздании отчёта
              </span>
            )}
            {url && (
              <a
                className="inline-flex items-center gap-1 font-medium text-ink underline-offset-2 hover:underline"
                href={url}
                rel="noreferrer"
                target="_blank"
              >
                Открыть коммит <ExternalLink className="size-3" />
              </a>
            )}
          </div>
        </div>
      )}
    </li>
  );
}

function Detail({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <p className="mb-0.5 text-[10px] font-semibold uppercase tracking-[0.12em] text-ink/70">
        {title}
      </p>
      {children}
    </div>
  );
}

function DetailList({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) return null;
  return (
    <Detail title={title}>
      <ul className="list-disc space-y-0.5 pl-4">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </Detail>
  );
}

function riskGroup(risk: RiskLevel): Exclude<RiskFilter, "ALL"> {
  if (risk === "HIGH" || risk === "MEDIUM") return "ATTENTION";
  if (risk === "LOW") return "LOW";
  return "NONE";
}
