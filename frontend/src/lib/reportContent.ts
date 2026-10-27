import type { CommitInsight } from "@/lib/types";
import { commitUrl } from "@/lib/utils";

export interface ReportSection {
  id: string;
  title: string;
  body: string;
}

export interface ParsedReport {
  /** Пункты раздела «Главное» — показываются отдельной карточкой. */
  summary: string[];
  sections: ReportSection[];
}

const SUMMARY_TITLE = "главное";
const COMMIT_REFERENCE = /`?\b([0-9a-f]{7,40})\b`?/gi;

/** Делит Markdown отчёта на разделы второго уровня. */
export function parseReportSections(markdown: string): ParsedReport {
  const summary: string[] = [];
  const sections: ReportSection[] = [];
  const preamble: string[] = [];
  let current: { title: string; lines: string[] } | null = null;
  let inCode = false;

  const flush = () => {
    if (!current) return;
    const body = current.lines.join("\n").trim();
    if (current.title.toLowerCase() === SUMMARY_TITLE) {
      summary.push(...extractItems(body));
    } else {
      sections.push({
        id: `report-section-${sections.length + 1}`,
        title: current.title,
        body,
      });
    }
  };

  for (const line of markdown.replaceAll("\r\n", "\n").split("\n")) {
    if (line.trimStart().startsWith("```")) inCode = !inCode;
    const heading = inCode ? null : line.match(/^##\s+(.+?)\s*#*\s*$/);
    if (heading) {
      flush();
      current = {
        title: heading[1].replace(/^\d+[.)]\s*/, "").trim(),
        lines: [],
      };
      continue;
    }
    (current ? current.lines : preamble).push(line);
  }
  flush();

  const intro = preamble.join("\n").trim();
  if (intro) {
    sections.unshift({ id: "report-section-0", title: "Обзор", body: intro });
  }
  return { summary, sections };
}

/** Превращает короткие хэши, известные отчёту, в ссылки на коммиты. */
export function linkCommitReferences(
  markdown: string,
  commits: CommitInsight[],
  repositoryUrl: string,
) {
  if (!markdown || commits.length === 0) return markdown;

  const hashes = commits.map(commit => commit.commitHash.toLowerCase());
  // Existing links and fenced code are already structured; do not rewrite their destinations.
  return markdown.split(/(```[\s\S]*?```|!?\[[^\]]*\]\([^)]*\))/g).map(part => {
    if (part.startsWith("```") || /^!?\[[^\]]*\]\(/.test(part)) return part;
    return part.replace(COMMIT_REFERENCE, (match, hash: string) => {
      const matches = hashes.filter(full => full.startsWith(hash.toLowerCase()));
      if (matches.length !== 1) return match;
      const fullHash = matches[0];
      const url = commitUrl(repositoryUrl, fullHash);
      return url ? `[\`${fullHash.slice(0, 7)}\`](${url})` : `\`${fullHash.slice(0, 7)}\``;
    });
  }).join("");
}

function extractItems(body: string) {
  const items = body
    .split("\n")
    .map((line) => line.match(/^\s*(?:[-*+]|\d+[.)])\s+(.+)$/)?.[1]?.trim())
    .filter((item): item is string => Boolean(item));
  if (items.length > 0) return items;

  // Модель иногда пишет «Главное» сплошным абзацем — делим его на отдельные выводы.
  return body
    .split(/\n{2,}/)
    .map((paragraph) => paragraph.replace(/\s+/g, " ").trim())
    .filter(Boolean)
    .flatMap((paragraph) => paragraph.split(/(?<=[.!?])\s+(?=[А-ЯЁA-Z«(])/));
}
