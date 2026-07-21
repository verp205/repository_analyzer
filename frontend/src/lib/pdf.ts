import type { Content, TDocumentDefinitions } from "pdfmake/interfaces";

export interface PdfReportData {
  audience: string;
  content: string;
  taskId: number;
}

export async function createReportPdfBlob(report: PdfReportData) {
  const [pdfMakeModule, fontsModule] = await Promise.all([
    import("pdfmake/build/pdfmake"),
    import("pdfmake/build/vfs_fonts"),
  ]);

  const pdfMake = pdfMakeModule.default;
  pdfMake.addVirtualFileSystem(fontsModule.default);

  const definition: TDocumentDefinitions = {
    info: {
      author: "Иван Науменко",
      subject: `Анализ репозитория, задача №${report.taskId}`,
      title: `Отчёт для роли «${report.audience}»`,
    },
    pageMargins: [46, 54, 46, 48],
    defaultStyle: {
      color: "#343941",
      font: "Roboto",
      fontSize: 10,
      lineHeight: 1.35,
    },
    footer: (currentPage, pageCount) => ({
      alignment: "right",
      color: "#808792",
      fontSize: 8,
      margin: [46, 12, 46, 0],
      text: `${currentPage} / ${pageCount}`,
    }),
    content: [
      {
        color: "#747b86",
        fontSize: 9,
        bold: true,
        characterSpacing: 1.6,
        text: "АНАЛИЗАТОР РЕПОЗИТОРИЕВ",
      },
      {
        color: "#111318",
        fontSize: 24,
        bold: true,
        margin: [0, 16, 0, 7],
        text: `Отчёт для роли «${report.audience}»`,
      },
      {
        color: "#747b86",
        fontSize: 9,
        margin: [0, 0, 0, 22],
        text: `Задача №${report.taskId} · Создано Иваном Науменко`,
      },
      {
        canvas: [
          {
            lineColor: "#d3d7dd",
            lineWidth: 1,
            type: "line",
            x1: 0,
            x2: 503,
            y1: 0,
            y2: 0,
          },
        ],
        margin: [0, 0, 0, 20],
      },
      ...markdownToPdfContent(report.content),
    ],
    styles: {
      h1: {
        bold: true,
        color: "#111318",
        fontSize: 19,
        margin: [0, 8, 0, 10],
      },
      h2: {
        bold: true,
        color: "#1d2127",
        fontSize: 14,
        margin: [0, 16, 0, 7],
      },
      h3: {
        bold: true,
        color: "#252a32",
        fontSize: 11,
        margin: [0, 11, 0, 5],
      },
      paragraph: {
        margin: [0, 0, 0, 7],
      },
      code: {
        background: "#eef0f3",
        color: "#252a32",
        fontSize: 8,
        margin: [0, 3, 0, 9],
      },
      quote: {
        color: "#555d68",
        italics: true,
        margin: [14, 2, 0, 9],
      },
    },
  };

  return pdfMake.createPdf(definition).getBlob();
}

function markdownToPdfContent(markdown: string): Content[] {
  const content: Content[] = [];
  const lines = markdown.replaceAll("\r\n", "\n").split("\n");
  let codeBlock: string[] | null = null;
  let unorderedItems: string[] = [];
  let orderedItems: string[] = [];

  function flushLists() {
    if (unorderedItems.length > 0) {
      content.push({
        margin: [8, 0, 0, 9],
        ul: unorderedItems.map(cleanInlineMarkdown),
      });
      unorderedItems = [];
    }
    if (orderedItems.length > 0) {
      content.push({
        margin: [8, 0, 0, 9],
        ol: orderedItems.map(cleanInlineMarkdown),
      });
      orderedItems = [];
    }
  }

  for (const rawLine of lines) {
    const line = rawLine.trimEnd();

    if (line.trimStart().startsWith("```")) {
      flushLists();
      if (codeBlock) {
        content.push({ style: "code", text: codeBlock.join("\n") });
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

    const heading = trimmed.match(/^(#{1,3})\s+(.+)$/);
    if (heading) {
      content.push({
        style: `h${heading[1].length}`,
        text: cleanInlineMarkdown(heading[2]),
      });
      continue;
    }

    if (trimmed.startsWith(">")) {
      content.push({
        style: "quote",
        text: cleanInlineMarkdown(trimmed.replace(/^>\s?/, "")),
      });
      continue;
    }

    content.push({
      style: "paragraph",
      text: cleanInlineMarkdown(trimmed),
    });
  }

  flushLists();
  if (codeBlock?.length) {
    content.push({ style: "code", text: codeBlock.join("\n") });
  }
  return content;
}

function cleanInlineMarkdown(value: string) {
  return value
    .replace(/!\[([^\]]*)]\([^)]+\)/g, "$1")
    .replace(/\[([^\]]+)]\(([^)]+)\)/g, "$1 ($2)")
    .replace(/(\*\*|__)(.*?)\1/g, "$2")
    .replace(/(\*|_)(.*?)\1/g, "$2")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/<[^>]+>/g, "");
}
