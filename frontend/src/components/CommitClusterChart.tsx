import type { ChangeCluster } from "@/lib/types";
import { formatNumber } from "@/lib/utils";

const colors: Record<string, string> = {
  CORE: "#b98e68",
  TESTS: "#7f9882",
  SECURITY: "#b47d78",
  DOCUMENTATION: "#8b84a4",
  BUILD_AND_CONFIGURATION: "#c1a65f",
  DATA: "#789ba3",
  API: "#9aa6b8",
};
const percentFormatter = new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 1 });
const plurals = new Intl.PluralRules("ru-RU");

interface Segment {
  key: string;
  label: string;
  count: number;
  share: number;
  start: number;
  color: string;
}

export function CommitClusterChart({ clusters }: { clusters: ChangeCluster[] }) {
  const visible = clusters.filter(cluster => cluster.commits.length > 0);
  const total = visible.reduce((sum, cluster) => sum + cluster.commits.length, 0);
  if (total === 0) {
    return <p className="text-sm text-muted">Пока нет завершённых изменений для группировки.</p>;
  }

  const segments: Segment[] = visible.map((cluster, index) => ({
    key: cluster.type,
    label: cluster.displayName,
    count: cluster.commits.length,
    share: cluster.commits.length / total * 100,
    start: visible.slice(0, index).reduce((sum, previous) => sum + previous.commits.length, 0) / total * 100,
    color: colors[cluster.type] ?? "#9aa6b8",
  }));
  const description = segments.map(segment => `${segment.label}: ${caption(segment)}`).join("; ");

  return (
    <figure aria-label={`Распределение ${formatNumber(total)} ${commitWord(total)} по группам. ${description}`}
      className="@container" role="img">
      <svg aria-hidden="true" className="hidden w-full overflow-visible @[340px]:block" viewBox="0 0 420 300">
        <Ring cx={210} cy={150} radius={78} segments={segments} total={total} width={26} />
        {labels(segments).map(label => (
          <g key={label.segment.key}>
            <path d={`M ${label.anchor.x} ${label.anchor.y} L ${label.elbowX} ${label.anchor.y} L ${label.elbowX} ${label.y} L ${label.lineEndX} ${label.y}`}
              fill="none" opacity={0.65} stroke={label.segment.color} strokeWidth={1} />
            <circle cx={label.anchor.x} cy={label.anchor.y} fill={label.segment.color} r={2} />
            <text fill="var(--ink)" fontSize={11.5} fontWeight={500} textAnchor={label.side === "right" ? "start" : "end"}
              x={label.textX} y={label.y - 4 - (label.lines.length - 1) * 13}>
              {label.lines.map((line, index) => <tspan dy={index === 0 ? 0 : 13} key={index} x={label.textX}>{line}</tspan>)}
            </text>
            <text fill="var(--muted)" fontSize={10.5} textAnchor={label.side === "right" ? "start" : "end"}
              x={label.textX} y={label.y + 12}>{caption(label.segment)}</text>
          </g>
        ))}
      </svg>

      <div aria-hidden="true" className="@[340px]:hidden">
        <svg className="mx-auto w-full max-w-[220px]" viewBox="0 0 160 160">
          <Ring cx={80} cy={80} radius={62} segments={segments} total={total} width={20} />
        </svg>
        <ul className="mt-5 grid grid-cols-2 gap-x-4 gap-y-3">
          {segments.map(segment => (
            <li className="flex items-start gap-2" key={segment.key}>
              <span className="mt-1 size-2 shrink-0 rounded-full" style={{ backgroundColor: segment.color }} />
              <div className="min-w-0 text-xs leading-5">
                <p className="text-ink">{segment.label}</p>
                <p className="text-[11px] text-muted">{caption(segment)}</p>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </figure>
  );
}

function Ring({ cx, cy, radius, width, segments, total }: {
  cx: number; cy: number; radius: number; width: number; segments: Segment[]; total: number;
}) {
  return (
    <g>
      <circle cx={cx} cy={cy} fill="none" r={radius} stroke="var(--line)" strokeWidth={width} />
      {segments.map(segment => (
        <circle className="transition-opacity duration-200 hover:opacity-80 motion-reduce:transition-none"
          cx={cx} cy={cy} fill="none" key={segment.key} pathLength={100} r={radius}
          stroke={segment.color} strokeDasharray={`${segment.share} ${100 - segment.share}`}
          strokeDashoffset={-segment.start} strokeWidth={width} transform={`rotate(-90 ${cx} ${cy})`}>
          <title>{segment.label}: {caption(segment)}</title>
        </circle>
      ))}
      {segments.length > 1 && segments.map(segment => {
        const inner = point(cx, cy, radius - width / 2, segment.start);
        const outer = point(cx, cy, radius + width / 2, segment.start);
        return <path d={`M ${inner.x} ${inner.y} L ${outer.x} ${outer.y}`} key={`gap-${segment.key}`}
          stroke="var(--panel)" strokeWidth={1.5} />;
      })}
      <text fill="var(--ink)" fontFamily="var(--font-display)" fontSize={radius > 70 ? 30 : 25}
        fontWeight={600} textAnchor="middle" x={cx} y={cy + 2}>{formatNumber(total)}</text>
      <text fill="var(--muted)" fontSize={10} textAnchor="middle" x={cx} y={cy + 22}>{commitWord(total)}</text>
    </g>
  );
}

function labels(segments: Segment[]) {
  const result = segments.map(segment => {
    const anchor = point(210, 150, 94, segment.start + segment.share / 2);
    const side = anchor.x >= 210 ? "right" : "left";
    return { segment, anchor, side, y: Math.max(28, Math.min(272, anchor.y)),
      elbowX: side === "right" ? 308 : 112, lineEndX: side === "right" ? 314 : 106,
      textX: side === "right" ? 318 : 102, lines: wrapLabel(segment.label) };
  });
  for (const side of ["left", "right"]) {
    const column = result.filter(label => label.side === side).sort((a, b) => a.y - b.y);
    column.forEach((label, index) => { label.elbowX += side === "right" ? index : -index; });
    for (let i = 1; i < column.length; i++) column[i].y = Math.max(column[i].y, column[i - 1].y + 44);
    if (column.length && column[column.length - 1].y > 272) {
      column[column.length - 1].y = 272;
      for (let i = column.length - 2; i >= 0; i--) column[i].y = Math.min(column[i].y, column[i + 1].y - 44);
    }
  }
  return result;
}

function point(cx: number, cy: number, radius: number, percent: number) {
  const angle = percent / 100 * Math.PI * 2 - Math.PI / 2;
  return { x: cx + Math.cos(angle) * radius, y: cy + Math.sin(angle) * radius };
}

function wrapLabel(label: string) {
  const lines: string[] = [];
  for (const word of label.split(" ")) {
    const last = lines.length - 1;
    if (last >= 0 && lines[last].length + word.length < 16) lines[last] += ` ${word}`;
    else lines.push(word);
  }
  return lines;
}

function caption(segment: Segment) {
  return `${formatNumber(segment.count)} · ${percentFormatter.format(segment.share)}%`;
}

function commitWord(count: number) {
  const plural = plurals.select(count);
  return plural === "one" ? "коммит" : plural === "few" ? "коммита" : "коммитов";
}
