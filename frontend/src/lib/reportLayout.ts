import type { CommitInsight, ReportAudience, RepositoryFacts, RiskLevel } from "@/lib/types";

export type KeyNumberId = "period" | "commits" | "authors" | "lines" | "risks";

/**
 * Какие показатели нужны читателю. Отчёт, в одинаковом виде показывающий
 * всем авторов, строки и файлы, превращается в шум: безопаснику не нужны
 * доли авторов, руководителю — горячие файлы.
 */
export interface ReportLayout {
  keyNumbers: KeyNumberId[];
  activity: boolean;
  categories: boolean;
  authors: boolean;
  hotspots: boolean;
  /** Коммиты с высоким и средним риском отдельным разделом. */
  attentionCommits: boolean;
}

const layouts: Record<ReportAudience, ReportLayout> = {
  DEVELOPER: {
    keyNumbers: ["period", "commits", "lines", "risks"],
    activity: false,
    categories: true,
    authors: false,
    hotspots: true,
    attentionCommits: true,
  },
  ANALYST: {
    keyNumbers: ["period", "commits", "authors"],
    activity: true,
    categories: true,
    authors: false,
    hotspots: false,
    attentionCommits: false,
  },
  SECURITY: {
    keyNumbers: ["period", "commits", "risks"],
    activity: false,
    categories: false,
    authors: false,
    hotspots: true,
    attentionCommits: true,
  },
  TEAM_LEAD: {
    keyNumbers: ["period", "commits", "authors", "lines", "risks"],
    activity: true,
    categories: true,
    authors: true,
    hotspots: true,
    attentionCommits: true,
  },
  DIRECTOR: {
    keyNumbers: ["period", "commits", "authors", "risks"],
    activity: true,
    categories: true,
    authors: false,
    hotspots: false,
    attentionCommits: false,
  },
};

export function reportLayout(audience: ReportAudience): ReportLayout {
  return layouts[audience];
}

/** Есть ли что показать в блоке показателей, кроме пустых разделов. */
export function hasFactBlocks(layout: ReportLayout, facts: RepositoryFacts, withActivity = true) {
  return (
    (withActivity && layout.activity && facts.activity.length > 1) ||
    (layout.categories && facts.categories.length > 0) ||
    (layout.authors && facts.authors.length > 1) ||
    (layout.hotspots && facts.hotspots.length > 0)
  );
}

/** Коммиты с высоким, затем средним риском; новые выше. */
export function attentionCommits(commits: CommitInsight[]) {
  return commits
    .filter((commit) => commit.riskLevel === "HIGH" || commit.riskLevel === "MEDIUM")
    .sort(
      (left, right) =>
        riskRank(right.riskLevel) - riskRank(left.riskLevel) || right.position - left.position,
    );
}

export type FindingTone = "high" | "medium" | "low";

/** Метки находок, которые модель ставит в начале пункта: **[Подтверждено]**, **[Средний]**. */
export const FINDING_TAG = /^\[(Подтверждено|Проверить|Высокий|Средний|Низкий)]$/;

export function findingTone(tag: string): FindingTone {
  if (tag.includes("Подтверждено") || tag.includes("Высокий")) return "high";
  if (tag.includes("Проверить") || tag.includes("Средний")) return "medium";
  return "low";
}

function riskRank(risk: RiskLevel) {
  return { HIGH: 3, MEDIUM: 2, LOW: 1, NONE: 0, UNKNOWN: -1 }[risk];
}
