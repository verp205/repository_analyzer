import { riskLabel } from "@/lib/labels";
import { linkCommitReferences, type ReportSection } from "@/lib/reportContent";
import {
  attentionCommits,
  hasFactBlocks,
  reportLayout,
  type KeyNumberId,
} from "@/lib/reportLayout";
import type { CommitInsight, ReportAudience, RepositoryFacts } from "@/lib/types";
import { commitUrl, formatDate, formatNumber } from "@/lib/utils";

export interface MarkdownReportData {
  title: string;
  subtitle: string;
  audience: ReportAudience;
  summary: string[];
  sections: ReportSection[];
  facts: RepositoryFacts | null;
  commits: CommitInsight[];
  repositoryUrl: string;
}

const ATTENTION_LIMIT = 15;

/** Markdown для вставки в Confluence, Notion или README. */
export function buildReportMarkdown(data: MarkdownReportData) {
  const layout = reportLayout(data.audience);
  const link = (text: string) =>
    linkCommitReferences(text, data.commits, data.repositoryUrl);
  const lines: string[] = [`# ${data.title}`, "", `_${data.subtitle}_`, ""];

  if (data.summary.length > 0) {
    lines.push("## Главное", "", ...data.summary.map((item) => `- ${link(item)}`), "");
  }

  if (data.facts) {
    const facts = data.facts;
    const rows: Record<KeyNumberId, string> = {
      period: `| Период | ${formatDate(facts.firstCommitAt)} — ${formatDate(facts.lastCommitAt)} (${facts.periodDays} дн.) |`,
      commits: `| Коммиты | ${formatNumber(facts.totalCommits)}, из них слияний ${formatNumber(facts.mergeCommits)} |`,
      authors: `| Авторы | ${formatNumber(facts.authorCount)} |`,
      lines: `| Изменено строк | +${formatNumber(facts.addedLines)} / −${formatNumber(facts.deletedLines)} |`,
      risks: `| Риски в коммитах | высокий: ${facts.risks.high}, средний: ${facts.risks.medium} |`,
    };
    lines.push(
      "## Ключевые показатели",
      "",
      "| Показатель | Значение |",
      "| --- | --- |",
      ...layout.keyNumbers.map((id) => rows[id]),
      "",
    );
  }

  for (const section of data.sections) {
    lines.push(`## ${section.title}`, "", link(section.body), "");
  }

  if (data.facts && hasFactBlocks(layout, data.facts, false)) {
    const facts = data.facts;
    lines.push("## Показатели за период", "");
    if (layout.categories && facts.categories.length > 0) {
      lines.push(
        "### Характер изменений",
        "",
        ...facts.categories.map((category) => `- ${category.displayName} — ${category.commits}`),
        "",
      );
    }
    if (layout.authors && facts.authors.length > 1) {
      lines.push(
        "### Авторы",
        "",
        "| Автор | Коммиты | Доля |",
        "| --- | ---: | ---: |",
        ...facts.authors.map(
          (author) =>
            `| ${cell(author.name)} | ${author.commits} | ${String(author.share).replace(".", ",")}% |`,
        ),
        "",
      );
    }
    if (layout.hotspots && facts.hotspots.length > 0) {
      lines.push(
        "### Часто изменяемые файлы",
        "",
        "| Файл | Коммиты |",
        "| --- | ---: |",
        ...facts.hotspots.map((hotspot) => `| \`${cell(hotspot.path)}\` | ${hotspot.commits} |`),
        "",
      );
    }
  }

  const attention = layout.attentionCommits ? attentionCommits(data.commits) : [];
  if (attention.length > 0) {
    lines.push("## Коммиты, требующие внимания", "");
    for (const commit of attention.slice(0, ATTENTION_LIMIT)) {
      const hash = commit.commitHash.slice(0, 7);
      const url = commitUrl(data.repositoryUrl, commit.commitHash);
      const reference = url ? `[\`${hash}\`](${url})` : `\`${hash}\``;
      const pullRequest = commit.details.pullRequest;
      const pullRequestLink = pullRequest
        ? ` · ${pullRequest.url ? `[PR #${pullRequest.number}](${pullRequest.url})` : `PR #${pullRequest.number}`}`
        : "";
      lines.push(
        `- **${riskLabel(commit.riskLevel)} риск** · ${reference} · ${formatDate(commit.committedAt)} · ${commit.categoryName}${pullRequestLink} — ${commit.summary}`,
        ...[...new Set([...commit.details.risks, ...commit.details.securityNotes])].map(
          (note) => `  - ${note}`,
        ),
      );
    }
    if (attention.length > ATTENTION_LIMIT) {
      lines.push("", `_И ещё ${attention.length - ATTENTION_LIMIT} коммитов с повышенным риском._`);
    }
    lines.push("");
  }

  return `${lines.join("\n").replace(/\n{3,}/g, "\n\n").trim()}\n`;
}

function cell(value: string) {
  return value.replace(/\|/g, "\\|").replace(/\n/g, " ");
}
