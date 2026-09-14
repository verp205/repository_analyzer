import type { Content, TDocumentDefinitions } from "pdfmake/interfaces";
import { riskLabel } from "@/lib/labels";
import type { ReportSection } from "@/lib/reportContent";
import {
  attentionCommits,
  FINDING_TAG,
  findingTone,
  hasFactBlocks,
  reportLayout,
  type KeyNumberId,
  type ReportLayout,
} from "@/lib/reportLayout";
import type { CommitInsight, ReportAudience, RepositoryFacts } from "@/lib/types";
import { formatDate, formatNumber } from "@/lib/utils";

export interface PdfReportData {
  title: string;
  subtitle: string;
  audience: ReportAudience;
  summary: string[];
  sections: ReportSection[];
  facts: RepositoryFacts | null;
  commits: CommitInsight[];
}

const colors = {
  ink: "#111318",
  text: "#343941",
  muted: "#747b86",
  line: "#d3d7dd",
  soft: "#f1f3f5",
  high: "#b42318",
  medium: "#b54708",
  low: "#475467",
};

const ATTENTION_LIMIT = 15;
/** Оглавление нужно только длинному документу. */
const TOC_MIN_SECTIONS = 7;
/** Небольшую таблицу не разрываем между страницами вместе с заголовком. */
const UNBREAKABLE_ROWS = 12;

export async function createReportPdfBlob(report: PdfReportData) {
  const [pdfMakeModule, fontsModule] = await Promise.all([
    import("pdfmake/build/pdfmake"),
    import("pdfmake/build/vfs_fonts"),
  ]);

  const pdfMake = pdfMakeModule.default;
  pdfMake.addVirtualFileSystem(fontsModule.default);
  const layout = reportLayout(report.audience);

  const content: Content[] = [
    { text: report.title, style: "title" },
    { text: report.subtitle, style: "meta" },
    {
      canvas: [
        { lineColor: colors.line, lineWidth: 1, type: "line", x1: 0, x2: 503, y1: 0, y2: 0 },
      ],
      margin: [0, 0, 0, 16],
    },
  ];

  if (report.summary.length > 0) content.push(summaryBox(report.summary));
  if (report.facts) content.push(keyNumbers(report.facts, layout));
  if (report.sections.length >= TOC_MIN_SECTIONS) {
    content.push({
      toc: { title: { text: "Содержание", style: "h2" } },
      margin: [0, 0, 0, 8],
    } as Content);
  }
  for (const section of report.sections) {
    content.push(heading(section.title));
    content.push(...markdownToPdfContent(section.body));
  }
  if (report.facts && hasFactBlocks(layout, report.facts, false)) {
    content.push(...factsContent(report.facts, layout));
  }
  if (layout.attentionCommits) content.push(...attentionContent(report.commits));

  const definition: TDocumentDefinitions = {
    info: {
      title: report.title,
      subject: "Анализ истории Git-репозитория",
    },
    pageMargins: [46, 50, 46, 50],
    defaultStyle: {
      color: colors.text,
      font: "Roboto",
      fontSize: 10,
      lineHeight: 1.35,
    },
    footer: (currentPage, pageCount) =>
      pageCount > 1
        ? {
            alignment: "right",
            color: colors.muted,
            fontSize: 8,
            margin: [46, 16, 46, 0],
            text: `${currentPage} / ${pageCount}`,
          }
        : null,
    content,
    styles: {
      title: { bold: true, color: colors.ink, fontSize: 18, lineHeight: 1.2, margin: [0, 0, 0, 6] },
      meta: { color: colors.muted, fontSize: 9, margin: [0, 0, 0, 14] },
      eyebrow: { bold: true, characterSpacing: 1, color: colors.muted, fontSize: 7.5 },
      number: { bold: true, color: colors.ink, fontSize: 12, margin: [0, 3, 0, 1] },
      note: { color: colors.muted, fontSize: 8 },
      h2: { bold: true, color: colors.ink, fontSize: 14, margin: [0, 14, 0, 6] },
      h3: { bold: true, color: colors.ink, fontSize: 11, margin: [0, 10, 0, 4] },
      paragraph: { margin: [0, 0, 0, 7] },
      tableHeader: { bold: true, color: colors.muted, fontSize: 8.5 },
      quote: { color: "#555d68", italics: true, margin: [14, 2, 0, 9] },
    },
  };

  return pdfMake.createPdf(definition).getBlob();
}

function heading(text: string): Content {
  return { text, style: "h2", tocItem: true } as Content;
}

function summaryBox(items: string[]): Content {
  return {
    table: {
      widths: ["*"],
      body: [
        [
          {
            stack: [
              { text: "ГЛАВНОЕ", style: "eyebrow" },
              { ul: items.map((item) => richText(item)), margin: [0, 6, 0, 0] },
            ],
            margin: [12, 10, 12, 12],
          },
        ],
      ],
    },
    layout: {
      fillColor: () => colors.soft,
      hLineWidth: () => 0,
      vLineWidth: () => 0,
    },
    margin: [0, 0, 0, 16],
  } as Content;
}

function keyNumbers(facts: RepositoryFacts, layout: ReportLayout): Content {
  const byId: Record<KeyNumberId, [string, string, string]> = {
    period: [
      "Период",
      `${facts.periodDays} дн.`,
      `${formatDate(facts.firstCommitAt)} — ${formatDate(facts.lastCommitAt)}`,
    ],
    commits: [
      "Коммиты",
      pdfNumber(facts.totalCommits),
      facts.mergeCommits ? `слияний: ${pdfNumber(facts.mergeCommits)}` : "без слияний",
    ],
    authors: ["Авторы", pdfNumber(facts.authorCount), facts.authors[0]?.name ?? "—"],
    lines: [
      "Строки",
      `+${pdfNumber(facts.addedLines)} / -${pdfNumber(facts.deletedLines)}`,
      `файлов: ${pdfNumber(facts.touchedFiles)}`,
    ],
    risks: ["Риски в коммитах", `${facts.risks.high} / ${facts.risks.medium}`, "высокий / средний"],
  };
  const cells = layout.keyNumbers.map((id) => byId[id]);

  return {
    table: {
      widths: cells.map(() => "*"),
      body: [
        cells.map(([label, value, note], index) => ({
          stack: [
            { text: label.toUpperCase(), style: "eyebrow" },
            {
              text: value,
              style: "number",
              color:
                layout.keyNumbers[index] === "risks" && facts.risks.high > 0 ? colors.high : colors.ink,
            },
            { text: note, style: "note" },
          ],
        })),
      ],
    },
    layout: "noBorders",
    margin: [0, 0, 0, 12],
  } as Content;
}

function factsContent(facts: RepositoryFacts, layout: ReportLayout): Content[] {
  const result: Content[] = [heading("Показатели за период")];

  if (layout.categories && facts.categories.length > 0) {
    result.push({
      unbreakable: true,
      stack: [
        { text: "Характер изменений", style: "h3" },
        {
          ul: facts.categories.map((category) => `${category.displayName} — ${category.commits}`),
          margin: [8, 0, 0, 8],
        },
      ],
    } as Content);
  }
  if (layout.authors && facts.authors.length > 1) {
    result.push(
      titledTable(
        "Авторы",
        ["Автор", "Коммиты", "Доля"],
        facts.authors.map((author) => [
          author.name,
          pdfNumber(author.commits),
          `${String(author.share).replace(".", ",")}%`,
        ]),
        ["*", 60, 50],
      ),
    );
  }
  if (layout.hotspots && facts.hotspots.length > 0) {
    result.push(
      titledTable(
        "Часто изменяемые файлы",
        ["Файл", "Коммиты"],
        facts.hotspots.map((hotspot) => [hotspot.path, pdfNumber(hotspot.commits)]),
        ["*", 60],
      ),
    );
  }
  return result;
}

function attentionContent(commits: CommitInsight[]): Content[] {
  const attention = attentionCommits(commits);
  // Нулевые риски уже видны в ключевых показателях — пустой раздел не нужен.
  if (attention.length === 0) return [];

  const result: Content[] = [heading("Коммиты, требующие внимания")];
  for (const commit of attention.slice(0, ATTENTION_LIMIT)) {
    const notes = [...new Set([...commit.details.risks, ...commit.details.securityNotes])];
    result.push({
      unbreakable: true,
      margin: [0, 0, 0, 10],
      stack: [
        {
          fontSize: 8.5,
          text: [
            {
              text: `${riskLabel(commit.riskLevel)} риск`,
              bold: true,
              color: commit.riskLevel === "HIGH" ? colors.high : colors.medium,
            },
            {
              text: `  ·  ${commit.commitHash.slice(0, 7)}  ·  ${formatDate(commit.committedAt)}  ·  ${commit.categoryName}`,
              color: colors.muted,
            },
          ],
        },
        { text: commit.summary, margin: [0, 2, 0, 2] },
        ...(notes.length > 0
          ? [{ ul: notes.map((note) => ({ text: note, fontSize: 9 })), margin: [8, 0, 0, 0] }]
          : []),
      ],
    } as Content);
  }

  if (attention.length > ATTENTION_LIMIT) {
    result.push({
      text: `Ещё ${attention.length - ATTENTION_LIMIT} — в полном списке коммитов в приложении.`,
      style: "note",
    });
  }
  return result;
}

function titledTable(
  title: string,
  header: string[],
  rows: string[][],
  widths: (string | number)[],
): Content {
  return {
    unbreakable: rows.length <= UNBREAKABLE_ROWS,
    stack: [
      { text: title, style: "h3" },
      {
        table: {
          headerRows: 1,
          dontBreakRows: true,
          widths,
          body: [
            header.map((text) => ({ text, style: "tableHeader" })),
            ...rows.map((row) => row.map((text) => ({ text, fontSize: 9 }))),
          ],
        },
        layout: "lightHorizontalLines",
        margin: [0, 0, 0, 10],
      },
    ],
  } as Content;
}

function markdownToPdfContent(markdown: string): Content[] {
  const content: Content[] = [];
  const lines = markdown.replaceAll("\r\n", "\n").split("\n");
  let codeBlock: string[] | null = null;
  let unorderedItems: string[] = [];
  let orderedItems: string[] = [];

  function flushLists() {
    if (unorderedItems.length > 0) {
      content.push({ margin: [8, 0, 0, 9], ul: unorderedItems.map((item) => richText(item)) });
      unorderedItems = [];
    }
    if (orderedItems.length > 0) {
      content.push({ margin: [8, 0, 0, 9], ol: orderedItems.map((item) => richText(item)) });
      orderedItems = [];
    }
  }

  for (const rawLine of lines) {
    const line = rawLine.trimEnd();

    if (line.trimStart().startsWith("```")) {
      flushLists();
      if (codeBlock) {
        content.push({ text: codeBlock.join("\n"), fontSize: 8, color: colors.ink, margin: [0, 3, 0, 9] });
        codeBlock = null;
      } else {
        codeBlock = [];
      }
      continue;
    }

    if (codeBlock) {
      codeBlock.push(rawLine);
      continue;
    }

    const unordered = line.match(/^\s*[-*+]\s+(.+)$/);
    if (unordered) {
      if (orderedItems.length > 0) flushLists();
      unorderedItems.push(unordered[1]);
      continue;
    }

    const ordered = line.match(/^\s*\d+[.)]\s+(.+)$/);
    if (ordered) {
      if (unorderedItems.length > 0) flushLists();
      orderedItems.push(ordered[1]);
      continue;
    }

    flushLists();
    const trimmed = line.trim();
    if (!trimmed) continue;

    const headingMatch = trimmed.match(/^#{1,6}\s+(.+)$/);
    if (headingMatch) {
      content.push(richText(headingMatch[1], { style: "h3" }));
      continue;
    }

    if (trimmed.startsWith(">")) {
      content.push(richText(trimmed.replace(/^>\s?/, ""), { style: "quote" }));
      continue;
    }

    content.push(richText(trimmed, { style: "paragraph" }));
  }

  flushLists();
  if (codeBlock?.length) {
    content.push({ text: codeBlock.join("\n"), fontSize: 8, margin: [0, 3, 0, 9] });
  }
  return content;
}

/** Строка Markdown в текст pdfmake с сохранением жирного шрифта, кода и меток находок. */
function richText(value: string, extra: Record<string, unknown> = {}): Content {
  const cleaned = value
    .replace(/!\[([^\]]*)]\([^)]+\)/g, "$1")
    .replace(/\[([^\]]+)]\((?!\s)[^)]+\)/g, "$1")
    .replace(/<[^>]+>/g, "");

  const runs = cleaned
    .split(/(\*\*[^*]+\*\*|`[^`]+`)/g)
    .filter(Boolean)
    .map((part) => {
      if (part.length > 4 && part.startsWith("**") && part.endsWith("**")) {
        const inner = part.slice(2, -2);
        if (FINDING_TAG.test(inner)) {
          return {
            text: `${inner.slice(1, -1).toUpperCase()}  `,
            bold: true,
            fontSize: 8,
            characterSpacing: 0.4,
            color: colors[findingTone(inner)],
          };
        }
        return { text: inner, bold: true, color: colors.ink };
      }
      if (part.length > 2 && part.startsWith("`") && part.endsWith("`")) {
        return { text: part.slice(1, -1), color: colors.ink, background: colors.soft };
      }
      return { text: part.replace(/(^|\s)[*_](\S[^*_]*?)[*_](?=\s|$|[.,;:!?])/g, "$1$2") };
    });

  return { text: runs, ...extra } as Content;
}

function pdfNumber(value: number) {
  // Встроенный шрифт не содержит узкий неразрывный пробел из ru-RU формата.
  return formatNumber(value).replace(/\s/g, " ");
}
