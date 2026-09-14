import type { ReactNode } from "react";
import { hasFactBlocks, type KeyNumberId, type ReportLayout } from "@/lib/reportLayout";
import type { RepositoryFacts } from "@/lib/types";
import { cn, formatDate, formatNumber } from "@/lib/utils";

const monthFormatter = new Intl.DateTimeFormat("ru-RU", {
  month: "short",
  timeZone: "UTC",
  year: "numeric",
});

const dayFormatter = new Intl.DateTimeFormat("ru-RU", {
  day: "2-digit",
  month: "2-digit",
  timeZone: "UTC",
});

const granularityHints: Record<RepositoryFacts["activityGranularity"], string> = {
  DAY: "коммитов в день",
  WEEK: "коммитов в неделю",
  MONTH: "коммитов в месяц",
};

const gridColumns: Record<number, string> = {
  1: "grid-cols-1",
  2: "grid-cols-2",
  3: "grid-cols-2 sm:grid-cols-3",
  4: "grid-cols-2 lg:grid-cols-4",
  5: "grid-cols-2 sm:grid-cols-3 2xl:grid-cols-5",
};

export function KeyNumbers({ facts, layout }: { facts: RepositoryFacts; layout: ReportLayout }) {
  const tiles: Record<KeyNumberId, { label: string; value: ReactNode; note: string }> = {
    period: {
      label: "Период",
      value: `${formatNumber(facts.periodDays)} дн.`,
      note: `${formatDate(facts.firstCommitAt)} — ${formatDate(facts.lastCommitAt)}`,
    },
    commits: {
      label: "Коммиты",
      value: formatNumber(facts.totalCommits),
      note: facts.mergeCommits
        ? `из них слияний: ${formatNumber(facts.mergeCommits)}`
        : "без слияний",
    },
    authors: {
      label: "Авторы",
      value: formatNumber(facts.authorCount),
      note: facts.authors[0] ? `больше всех: ${facts.authors[0].name}` : "—",
    },
    lines: {
      label: "Изменено строк",
      value: `+${formatNumber(facts.addedLines)} / −${formatNumber(facts.deletedLines)}`,
      note: `затронуто файлов: ${formatNumber(facts.touchedFiles)}`,
    },
    risks: {
      label: "Риски в коммитах",
      value: (
        <>
          <span className={cn(facts.risks.high > 0 && "text-red-600 dark:text-red-300")}>
            {facts.risks.high}
          </span>
          <span className="px-1 text-muted">/</span>
          <span className={cn(facts.risks.medium > 0 && "text-amber-700 dark:text-amber-300")}>
            {facts.risks.medium}
          </span>
        </>
      ),
      note: "высокий / средний",
    },
  };

  return (
    <section
      aria-label="Ключевые показатели"
      className={cn(
        "report-anchor grid gap-2.5",
        gridColumns[layout.keyNumbers.length] ?? gridColumns[5],
      )}
      id="report-numbers"
    >
      {layout.keyNumbers.map((id) => (
        <div
          className="min-w-0 rounded-xl border border-line bg-panel-strong p-3.5"
          key={id}
        >
          <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-muted">
            {tiles[id].label}
          </div>
          <div className="mt-1.5 truncate font-display text-lg font-semibold text-ink">
            {tiles[id].value}
          </div>
          <div className="mt-0.5 truncate text-[11px] text-muted" title={tiles[id].note}>
            {tiles[id].note}
          </div>
        </div>
      ))}
    </section>
  );
}

export function ReportFacts({ facts, layout }: { facts: RepositoryFacts; layout: ReportLayout }) {
  if (!hasFactBlocks(layout, facts)) return null;
  const showActivity = layout.activity && facts.activity.length > 1;
  const showCategories = layout.categories && facts.categories.length > 0;
  const showAuthors = layout.authors && facts.authors.length > 1;
  const showHotspots = layout.hotspots && facts.hotspots.length > 0;

  return (
    <section className="report-anchor" id="report-facts">
      <h3 className="report-section-title">Показатели за период</h3>
      <p className="-mt-1 text-xs text-muted">
        Рассчитаны по истории коммитов без участия нейросети.
      </p>

      <div className="mt-5 grid gap-3 lg:grid-cols-2">
        {showActivity && (
          <FactCard hint={granularityHints[facts.activityGranularity]} title="Активность">
            <ActivityChart facts={facts} />
          </FactCard>
        )}

        {showCategories && (
          <FactCard hint="по результатам разбора коммитов" title="Характер изменений">
            <BarList
              items={facts.categories.map((category) => ({
                key: category.category,
                label: category.displayName,
                value: category.commits,
                caption: formatNumber(category.commits),
              }))}
            />
          </FactCard>
        )}

        {showAuthors && (
          <FactCard
            hint={
              facts.authorCount > facts.authors.length
                ? `показаны ${facts.authors.length} из ${facts.authorCount}`
                : undefined
            }
            title="Авторы"
          >
            <BarList
              items={facts.authors.map((author) => ({
                key: author.name,
                label: author.name,
                value: author.commits,
                caption: `${formatNumber(author.commits)} · ${String(author.share).replace(".", ",")}%`,
              }))}
            />
          </FactCard>
        )}

        {showHotspots && (
          <FactCard hint="сколько коммитов меняли файл" title="Часто изменяемые файлы">
            <ul className="divide-y divide-line/70">
              {facts.hotspots.map((hotspot) => (
                <li
                  className="flex items-baseline justify-between gap-3 py-1.5 text-xs"
                  key={hotspot.path}
                >
                  <span className="min-w-0 truncate font-mono text-ink" title={hotspot.path}>
                    {compactPath(hotspot.path)}
                  </span>
                  <span className="shrink-0 text-muted">{formatNumber(hotspot.commits)}</span>
                </li>
              ))}
            </ul>
            {facts.fileTypes.length > 0 && (
              <p className="mt-3 text-[11px] leading-5 text-muted">
                Типы файлов:{" "}
                {facts.fileTypes.map((type) => `${type.type} (${type.files})`).join(", ")}
              </p>
            )}
          </FactCard>
        )}
      </div>
    </section>
  );
}

function FactCard({
  title,
  hint,
  children,
}: {
  title: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="min-w-0 rounded-2xl border border-line bg-panel-strong p-4">
      <div className="mb-3">
        <p className="text-sm font-semibold text-ink">{title}</p>
        {hint && <p className="mt-0.5 text-[11px] text-muted">{hint}</p>}
      </div>
      {children}
    </div>
  );
}

function BarList({
  items,
}: {
  items: { key: string; label: string; value: number; caption: string }[];
}) {
  const max = Math.max(1, ...items.map((item) => item.value));
  return (
    <ul className="space-y-2.5">
      {items.map((item) => (
        <li key={item.key}>
          <div className="mb-1 flex items-baseline justify-between gap-3 text-xs">
            <span className="min-w-0 truncate text-ink">{item.label}</span>
            <span className="shrink-0 text-muted">{item.caption}</span>
          </div>
          <div className="h-1.5 overflow-hidden rounded-full bg-accent-soft">
            <div
              className="h-full rounded-full bg-accent/80"
              style={{ width: `${Math.max(3, (item.value / max) * 100)}%` }}
            />
          </div>
        </li>
      ))}
    </ul>
  );
}

function ActivityChart({ facts }: { facts: RepositoryFacts }) {
  const points = facts.activity;
  const max = Math.max(1, ...points.map((point) => point.commits));
  const first = points[0];
  const last = points[points.length - 1];

  return (
    <div>
      <div
        aria-label="Число коммитов по периодам"
        className="flex h-28 items-end gap-[2px]"
        role="img"
      >
        {points.map((point) => (
          <div
            className="flex h-full min-w-[2px] flex-1 items-end"
            key={point.period}
            title={`${periodLabel(point.period, facts.activityGranularity)}: ${point.commits}`}
          >
            <div
              className={cn(
                "w-full rounded-t-sm",
                point.commits > 0 ? "bg-accent/75 hover:bg-accent" : "bg-line",
              )}
              style={{
                height: point.commits > 0 ? `${Math.max(4, (point.commits / max) * 100)}%` : "1px",
              }}
            />
          </div>
        ))}
      </div>
      <div className="mt-2 flex justify-between text-[10px] text-muted">
        <span>{periodLabel(first.period, facts.activityGranularity)}</span>
        <span>максимум: {max}</span>
        <span>{periodLabel(last.period, facts.activityGranularity)}</span>
      </div>
    </div>
  );
}

function periodLabel(period: string, granularity: RepositoryFacts["activityGranularity"]) {
  const [year, month, day = 1] = period.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  if (granularity === "MONTH") return monthFormatter.format(date);
  const label = dayFormatter.format(date);
  return granularity === "WEEK" ? `нед. с ${label}` : label;
}

function compactPath(path: string) {
  const segments = path.split("/");
  return segments.length > 3 ? `…/${segments.slice(-2).join("/")}` : path;
}
